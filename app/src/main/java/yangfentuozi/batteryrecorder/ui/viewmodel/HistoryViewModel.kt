package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.HistoryRepository
import yangfentuozi.batteryrecorder.data.history.HistoryRepository.toFile
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.data.model.RecordDetailChartPoint
import yangfentuozi.batteryrecorder.data.model.normalizeRecordDetailChartPoints
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.utils.computePowerW
import java.io.File
import kotlin.math.roundToLong

data class RecordDetailChartUiState(
    // 原始展示点，保留完整时间精度，供原始曲线与辅助信息使用。
    val points: List<RecordDetailChartPoint> = emptyList(),
    // 趋势点，基于过滤后的原始点重新分桶生成，只用于趋势曲线。
    val trendPoints: List<RecordDetailChartPoint> = emptyList(),
    val minChartTime: Long? = null,
    val maxChartTime: Long? = null,
    val maxViewportStartTime: Long? = null,
    val viewportDurationMs: Long = 0L
)

class HistoryViewModel : ViewModel() {
    private val _records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val records: StateFlow<List<HistoryRecord>> = _records.asStateFlow()

    private val _recordDetail = MutableStateFlow<HistoryRecord?>(null)
    val recordDetail: StateFlow<HistoryRecord?> = _recordDetail.asStateFlow()

    // 图表 UI 只消费聚合后的 RecordDetailChartUiState，
    // 因此原始点和显示配置都收敛为 ViewModel 内部字段，避免对外暴露重复状态源。
    private val _recordChartUiState = MutableStateFlow(RecordDetailChartUiState())
    val recordChartUiState: StateFlow<RecordDetailChartUiState> = _recordChartUiState.asStateFlow()

    // recordPoints 保存从记录文件读取并完成“放电正负显示映射”后的原始点。
    // 它仍然保留 ChartPoint，是因为 computePowerW 前还需要读取原始功率字段。
    private var recordPoints: List<ChartPoint> = emptyList()
    // 这三个字段是图表派生状态的输入，不需要被外部订阅，因此使用普通字段即可。
    private var dualCellEnabled = ConfigConstants.DEF_DUAL_CELL_ENABLED
    private var calibrationValue = ConfigConstants.DEF_CALIBRATION_VALUE
    private var recordScreenOffEnabled = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 是否正在加载下一页；用于防止滚动触底时重复触发并发分页请求。
    private val _isPaging = MutableStateFlow(false)
    val isPaging: StateFlow<Boolean> = _isPaging.asStateFlow()

    // 当前筛选类型下是否还有未加载的历史文件。
    private val _hasMoreRecords = MutableStateFlow(false)
    val hasMoreRecords: StateFlow<Boolean> = _hasMoreRecords.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _chargeCapacityChangeFilter = MutableStateFlow<Int?>(null)
    val chargeCapacityChangeFilter: StateFlow<Int?> = _chargeCapacityChangeFilter.asStateFlow()

    // 分页上下文：
    // listFiles / loadedRecordCount 描述“总数据集 + 当前游标”。
    // latestListFile 用于沿用统计缓存策略（最新文件不读缓存）。
    // listLoadToken 用于隔离不同轮次加载，避免旧协程回写新状态。
    private var listFiles: List<File> = emptyList()
    private var allListRecordsCache: List<HistoryRecord>? = null
    private var pagedSourceRecords: List<HistoryRecord>? = null
    private var latestListFile: File? = null
    private var loadedRecordCount = 0
    private var listDischargeDisplayPositive = ConfigConstants.DEF_DISCHARGE_DISPLAY_POSITIVE
    private var currentListType: BatteryStatus? = null
    private var hasInitializedListContext = false
    private var listLoadToken: Long = 0L

    private companion object {
        const val PAGE_SIZE = 10
        // 目标不是“固定桶时长”，而是让不同记录长度大致映射到相近数量的趋势点。
        const val TARGET_TREND_BUCKET_COUNT = 240L
    }

    fun loadRecords(context: Context, type: BatteryStatus) {
        if (_isLoading.value) return
        if (currentListType == type && hasInitializedListContext) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dischargeDisplayPositive = getDischargeDisplayPositive(context)
                val files = withContext(Dispatchers.IO) {
                    HistoryRepository.listRecordFiles(context, type)
                }
                // 每次重新加载列表都推进 token，之前尚未完成的分页任务将被视为过期结果。
                val token = listLoadToken + 1
                listLoadToken = token
                currentListType = type
                listFiles = files
                allListRecordsCache = null
                pagedSourceRecords = null
                latestListFile = files.firstOrNull()
                listDischargeDisplayPositive = dischargeDisplayPositive
                hasInitializedListContext = true
                _chargeCapacityChangeFilter.value = null
                resetDisplayedRecords(files.size)
                // 首屏仅加载第一页；后续由 UI 滚动触底显式触发。
                loadNextPageInternal(context, token)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadNextPage(context: Context, type: BatteryStatus) {
        // 仅在“同一类型 + 非主加载 + 非分页中 + 仍有更多”时允许翻页。
        if (_isLoading.value || _isPaging.value || !_hasMoreRecords.value) return
        if (currentListType != type) return
        val token = listLoadToken
        viewModelScope.launch {
            loadNextPageInternal(context, token)
        }
    }

    fun updateChargeCapacityChangeFilter(context: Context, minCapacityChange: Int?) {
        if (_isLoading.value || currentListType != BatteryStatus.Charging) return
        val normalizedFilter = minCapacityChange?.takeIf { it > 0 }
        if (_chargeCapacityChangeFilter.value == normalizedFilter) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = listLoadToken + 1
                listLoadToken = token
                val nextPagedSourceRecords = when (normalizedFilter) {
                    null -> null
                    else -> ensureAllListRecordsCache(context, token).filter { record ->
                        computeChargingCapacityChange(record) >= normalizedFilter
                    }
                }
                if (token != listLoadToken) return@launch
                _chargeCapacityChangeFilter.value = normalizedFilter
                pagedSourceRecords = nextPagedSourceRecords
                resetDisplayedRecords(currentSourceCount())
                loadNextPageInternal(context, token)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadNextPageInternal(context: Context, token: Long) {
        // token 不一致代表列表上下文已切换（如充电/放电页切换），当前任务必须丢弃。
        if (token != listLoadToken || _isPaging.value || !_hasMoreRecords.value) return
        _isPaging.value = true
        try {
            val startIndex = loadedRecordCount
            val sourceRecords = pagedSourceRecords
            val sourceSize = sourceRecords?.size ?: listFiles.size
            val endExclusive = (startIndex + PAGE_SIZE).coerceAtMost(sourceSize)
            if (startIndex >= endExclusive) {
                _hasMoreRecords.value = false
                return
            }
            val nextPageRecords = sourceRecords?.subList(startIndex, endExclusive) ?: withContext(Dispatchers.IO) {
                val filesToLoad = listFiles.subList(startIndex, endExclusive).toList()
                val latestFile = latestListFile
                val dischargeDisplayPositive = listDischargeDisplayPositive
                filesToLoad.mapNotNull { file ->
                    buildHistoryRecord(context, file, latestFile, dischargeDisplayPositive)
                }
            }
            // I/O 返回后再次校验 token，避免旧任务覆盖新列表状态。
            if (token != listLoadToken) return
            if (nextPageRecords.isNotEmpty()) {
                _records.value += nextPageRecords
            }
            loadedRecordCount = endExclusive
            _hasMoreRecords.value = loadedRecordCount < currentSourceCount()
        } finally {
            if (token == listLoadToken) {
                _isPaging.value = false
            }
        }
    }

    fun loadRecord(context: Context, recordsFile: RecordsFile) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dischargeDisplayPositive = getDischargeDisplayPositive(context)
                val recordFile = recordsFile.toFile(context)
                if (recordFile == null) {
                    _userMessage.value = "记录文件不存在"
                    _recordDetail.value = null
                    recordPoints = emptyList()
                    recomputeRecordChartUiState()
                    return@launch
                }

                val (detail, points) = withContext(Dispatchers.IO) {
                    val detail = HistoryRepository.loadRecord(context, recordFile)
                    val points = mapChartPointsForDisplay(
                        points = HistoryRepository.loadRecordPoints(recordFile),
                        batteryStatus = recordsFile.type,
                        dischargeDisplayPositive = dischargeDisplayPositive
                    )
                    detail to points
                }
                _recordDetail.value = mapHistoryRecordForDisplay(detail, dischargeDisplayPositive)
                recordPoints = points
                recomputeRecordChartUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e<HistoryViewModel>("[记录详情] 加载失败: ${recordsFile.name}", tr = e)
                _userMessage.value = "加载记录详情失败"
                _recordDetail.value = null
                recordPoints = emptyList()
                recomputeRecordChartUiState()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteRecord(context: Context, recordsFile: RecordsFile) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deleted = withContext(Dispatchers.IO) {
                    HistoryRepository.deleteRecord(context, recordsFile)
                }
                if (deleted) {
                    val deletedName = recordsFile.name
                    val deletedSourceIndex = findSourceIndexByName(deletedName)
                    _records.value = _records.value.filter { it.asRecordsFile() != recordsFile }
                    if (currentListType == recordsFile.type) {
                        // 删除后同步修正数据源、筛选缓存与游标，避免翻页跳过未显示项。
                        listFiles = listFiles.filter { it.name != deletedName }
                        latestListFile = listFiles.firstOrNull()
                        allListRecordsCache = allListRecordsCache?.filter { record ->
                            record.name != deletedName
                        }
                        pagedSourceRecords = pagedSourceRecords?.filter { record ->
                            record.name != deletedName
                        }
                        if (deletedSourceIndex in 0 until loadedRecordCount) {
                            loadedRecordCount -= 1
                        }
                        loadedRecordCount = loadedRecordCount.coerceAtMost(currentSourceCount())
                        _hasMoreRecords.value = loadedRecordCount < currentSourceCount()
                    }
                    val detail = _recordDetail.value
                    if (detail != null && detail.asRecordsFile() == recordsFile) {
                        _recordDetail.value = null
                        recordPoints = emptyList()
                        recomputeRecordChartUiState()
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportRecord(
        context: Context,
        recordsFile: RecordsFile,
        destinationUri: Uri
    ) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    HistoryRepository.exportRecord(context, recordsFile, destinationUri)
                }
                _userMessage.value = "导出成功"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e<HistoryViewModel>("[导出] 单记录导出失败: ${recordsFile.name}", tr = e)
                _userMessage.value = "导出失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportAllRecords(
        context: Context,
        type: BatteryStatus,
        destinationUri: Uri
    ) {
        if (_isLoading.value) return
        val currentExportRecords = resolveCurrentExportRecords(type)
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val exportRecords = currentExportRecords ?: HistoryRepository
                        .listRecordFiles(context, type)
                        .map { file -> RecordsFile.fromFile(file) }
                    HistoryRepository.exportRecordsZip(context, exportRecords, destinationUri)
                }
                _userMessage.value = "导出成功"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e<HistoryViewModel>("[导出] 批量导出失败: ${type.dataDirName}", tr = e)
                _userMessage.value = "导出失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    private fun resetDisplayedRecords(sourceSize: Int) {
        loadedRecordCount = 0
        _records.value = emptyList()
        _isPaging.value = false
        _hasMoreRecords.value = sourceSize > 0
    }

    private fun currentSourceCount(): Int =
        pagedSourceRecords?.size ?: listFiles.size

    private suspend fun ensureAllListRecordsCache(
        context: Context,
        token: Long
    ): List<HistoryRecord> {
        allListRecordsCache?.let { return it }

        val latestFile = latestListFile
        val dischargeDisplayPositive = listDischargeDisplayPositive
        val records = withContext(Dispatchers.IO) {
            listFiles.mapNotNull { file ->
                buildHistoryRecord(context, file, latestFile, dischargeDisplayPositive)
            }
        }
        if (token != listLoadToken) return emptyList()
        allListRecordsCache = records
        return records
    }

    private fun buildHistoryRecord(
        context: Context,
        file: File,
        latestFile: File?,
        dischargeDisplayPositive: Boolean
    ): HistoryRecord? {
        return HistoryRepository.loadStats(context, file, file != latestFile)
            ?.let { historyRecord ->
                mapHistoryRecordForDisplay(historyRecord, dischargeDisplayPositive)
            }
    }

    private fun findSourceIndexByName(recordName: String): Int {
        val sourceRecords = pagedSourceRecords
        if (sourceRecords != null) {
            return sourceRecords.indexOfFirst { record -> record.name == recordName }
        }
        return listFiles.indexOfFirst { file -> file.name == recordName }
    }

    private fun resolveCurrentExportRecords(type: BatteryStatus): List<RecordsFile>? {
        if (currentListType != type) return null
        pagedSourceRecords?.let { records ->
            return records.map { record -> record.asRecordsFile() }
        }
        return listFiles.map { file -> RecordsFile.fromFile(file) }
    }

    private fun computeChargingCapacityChange(record: HistoryRecord): Int =
        record.stats.endCapacity - record.stats.startCapacity

    fun updatePowerDisplayConfig(
        dualCellEnabled: Boolean,
        calibrationValue: Int,
        recordScreenOffEnabled: Boolean
    ) {
        // 三个输入全部未变化时不重算，避免详情页设置流重复回放造成无意义的图表重建。
        if (
            this.dualCellEnabled == dualCellEnabled &&
            this.calibrationValue == calibrationValue &&
            this.recordScreenOffEnabled == recordScreenOffEnabled
        ) {
            return
        }
        this.dualCellEnabled = dualCellEnabled
        this.calibrationValue = calibrationValue
        this.recordScreenOffEnabled = recordScreenOffEnabled
        recomputeRecordChartUiState()
    }

    private fun recomputeRecordChartUiState() {
        // 第一步：把原始记录点换算成图表可直接消费的瓦特值模型。
        val displayPoints = mapDisplayPoints(
            detailType = _recordDetail.value?.type,
            rawPoints = recordPoints,
            dualCellEnabled = dualCellEnabled,
            calibrationValue = calibrationValue
        )
        // 第二步：趋势点始终基于“过滤后的展示点”计算，确保原始曲线和趋势曲线遵循同一展示语义。
        val filteredDisplayPoints = normalizeRecordDetailChartPoints(
            points = displayPoints,
            recordScreenOffEnabled = recordScreenOffEnabled
        )
        // 第三步：保留原始点给原始曲线/标记使用，同时额外生成趋势点给趋势曲线使用。
        val trendPoints = computeTrendPoints(filteredDisplayPoints)
        _recordChartUiState.value = computeViewportState(
            points = displayPoints,
            trendPoints = trendPoints
        )
    }

    private fun mapDisplayPoints(
        detailType: BatteryStatus?,
        rawPoints: List<ChartPoint>,
        dualCellEnabled: Boolean,
        calibrationValue: Int
    ): List<RecordDetailChartPoint> {
        // 详情页图表要求按时间有序；在这里统一排序，后续图表和趋势计算都不再重复关注文件顺序。
        val convertedPoints = rawPoints.sortedBy { it.timestamp }.map { point ->
            val displayPowerW = computePowerW(
                rawPower = point.power,
                dualCellEnabled = dualCellEnabled,
                calibrationValue = calibrationValue
            )
            // 充电记录理论上应显示正值；如果底层数据出现负值，这里直接裁成 0，避免把异常值带进坐标系。
            val rawPowerW = if (detailType == BatteryStatus.Charging && displayPowerW < 0) {
                0.0
            } else {
                displayPowerW
            }
            RecordDetailChartPoint(
                timestamp = point.timestamp,
                rawPowerW = rawPowerW,
                fittedPowerW = rawPowerW,
                capacity = point.capacity,
                isDisplayOn = point.isDisplayOn,
                temp = point.temp
            )
        }
        return convertedPoints
    }

    private fun computeViewportState(
        points: List<RecordDetailChartPoint>,
        trendPoints: List<RecordDetailChartPoint>
    ): RecordDetailChartUiState {
        // 全屏模式默认只显示总时长的 25%，让长记录进入详情页时能直接横向拖动浏览局部。
        val minChartTime = points.minOfOrNull { it.timestamp }
        val maxChartTime = points.maxOfOrNull { it.timestamp }
        val totalDurationMs = if (minChartTime != null && maxChartTime != null) {
            (maxChartTime - minChartTime).coerceAtLeast(1L)
        } else {
            0L
        }
        val viewportDurationMs = if (totalDurationMs > 0L) {
            (totalDurationMs * 0.25).roundToLong().coerceAtLeast(1L)
        } else {
            0L
        }
        val maxViewportStart = if (minChartTime != null && maxChartTime != null) {
            (maxChartTime - viewportDurationMs).coerceAtLeast(minChartTime)
        } else {
            null
        }

        return RecordDetailChartUiState(
            points = points,
            trendPoints = trendPoints,
            minChartTime = minChartTime,
            maxChartTime = maxChartTime,
            maxViewportStartTime = maxViewportStart,
            viewportDurationMs = viewportDurationMs
        )
    }

    private fun computeTrendPoints(points: List<RecordDetailChartPoint>): List<RecordDetailChartPoint> {
        if (points.isEmpty()) return emptyList()

        // 趋势图按总时长动态分桶，再用桶内中位数表达低频走势。
        // 这里不直接“平滑原始点”，而是先降采样为代表点，再把结果交给图表层画平滑路径，
        // 这样可以把“趋势语义”和“绘制样式”分离。
        val firstTimestamp = points.first().timestamp
        val lastTimestamp = points.last().timestamp
        val bucketDurationMs = computeTrendBucketDurationMs(lastTimestamp - firstTimestamp)
        val bucketPoints = LinkedHashMap<Long, MutableList<RecordDetailChartPoint>>()
        for (point in points) {
            val bucketIndex = (point.timestamp - firstTimestamp) / bucketDurationMs
            bucketPoints.getOrPut(bucketIndex) { ArrayList() } += point
        }

        return bucketPoints.map { (bucketIndex, pointsInBucket) ->
            val bucketStart = firstTimestamp + bucketIndex * bucketDurationMs
            val bucketEnd = (bucketStart + bucketDurationMs).coerceAtMost(lastTimestamp)
            val bucketCenterTimestamp = bucketStart + (bucketEnd - bucketStart) / 2L
            // 时间戳使用桶中心，而容量/温度/亮灭屏状态沿用最接近桶中心的原始点，
            // 这样辅助信息不会凭空插值，仍然来自真实采样。
            val representativePoint = pointsInBucket.minBy { point ->
                kotlin.math.abs(point.timestamp - bucketCenterTimestamp)
            }
            // 功率走势使用桶内中位数而不是平均值，目的是降低少量尖刺对趋势线的干扰。
            val powerValues = pointsInBucket.map { it.rawPowerW }.sorted()
            representativePoint.copy(
                timestamp = bucketCenterTimestamp,
                fittedPowerW = medianOfSorted(powerValues)
            )
        }
    }

    private fun computeTrendBucketDurationMs(totalDurationMs: Long): Long {
        // 先按目标桶数估算，再归一化到“人类可读”的时长阶梯，避免趋势点间距出现难理解的怪值。
        val rawBucketDurationMs = (totalDurationMs / TARGET_TREND_BUCKET_COUNT).coerceAtLeast(1_000L)
        return normalizeTrendBucketDurationMs(rawBucketDurationMs)
    }

    private fun normalizeTrendBucketDurationMs(rawBucketDurationMs: Long): Long {
        val readableDurationsMs = buildReadableTrendDurationsMs(rawBucketDurationMs)
        return readableDurationsMs.minBy { duration -> kotlin.math.abs(duration - rawBucketDurationMs) }
    }

    private fun buildReadableTrendDurationsMs(rawBucketDurationMs: Long): List<Long> {
        // 组合 1/2/3/5/10/15/20/30/60 秒与 10^n 倍数，生成一组可读的候选桶宽。
        val baseDurationsSeconds = listOf(1L, 2L, 3L, 5L, 10L, 15L, 20L, 30L, 60L)
        val maxDurationMs = rawBucketDurationMs * 10
        val candidates = LinkedHashSet<Long>()
        var scaleSeconds = 1L
        while (scaleSeconds * 1_000L <= maxDurationMs) {
            for (baseDurationSeconds in baseDurationsSeconds) {
                candidates += baseDurationSeconds * scaleSeconds * 1_000L
            }
            scaleSeconds *= 10L
        }
        return candidates.sorted()
    }

    private fun medianOfSorted(values: List<Double>): Double {
        // 输入已经预排序，因此这里只负责按奇偶长度取中位数，不再重复排序。
        val middleIndex = values.size / 2
        return if (values.size % 2 == 0) {
            (values[middleIndex - 1] + values[middleIndex]) / 2.0
        } else {
            values[middleIndex]
        }
    }
}
