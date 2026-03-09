package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
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
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.utils.computePowerW
import java.io.File
import kotlin.math.roundToLong

data class RecordDetailChartUiState(
    val displayPoints: List<ChartPoint> = emptyList(),
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

    private val _recordPoints = MutableStateFlow<List<ChartPoint>>(emptyList())
    val recordPoints: StateFlow<List<ChartPoint>> = _recordPoints.asStateFlow()

    private val _recordChartUiState = MutableStateFlow(RecordDetailChartUiState())
    val recordChartUiState: StateFlow<RecordDetailChartUiState> = _recordChartUiState.asStateFlow()

    private val _dualCellEnabled = MutableStateFlow(ConfigConstants.DEF_DUAL_CELL_ENABLED)
    private val _calibrationValue = MutableStateFlow(ConfigConstants.DEF_CALIBRATION_VALUE)

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

    // 分页上下文：
    // listFiles / loadedRecordCount 描述“总数据集 + 当前游标”。
    // latestListFile 用于沿用统计缓存策略（最新文件不读缓存）。
    // listLoadToken 用于隔离不同轮次加载，避免旧协程回写新状态。
    private var listFiles: List<File> = emptyList()
    private var latestListFile: File? = null
    private var loadedRecordCount = 0
    private var listDischargeDisplayPositive = ConfigConstants.DEF_DISCHARGE_DISPLAY_POSITIVE
    private var currentListType: BatteryStatus? = null
    private var listLoadToken: Long = 0L

    private companion object {
        const val PAGE_SIZE = 10
        const val TAG = "HistoryViewModel"
    }

    fun loadRecords(context: Context, type: BatteryStatus) {
        if (_isLoading.value) return
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
                latestListFile = files.firstOrNull()
                loadedRecordCount = 0
                listDischargeDisplayPositive = dischargeDisplayPositive
                _records.value = emptyList()
                _hasMoreRecords.value = files.isNotEmpty()
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

    private suspend fun loadNextPageInternal(context: Context, token: Long) {
        // token 不一致代表列表上下文已切换（如充电/放电页切换），当前任务必须丢弃。
        if (token != listLoadToken || _isPaging.value || !_hasMoreRecords.value) return
        _isPaging.value = true
        try {
            val startIndex = loadedRecordCount
            val endExclusive = (startIndex + PAGE_SIZE).coerceAtMost(listFiles.size)
            if (startIndex >= endExclusive) {
                _hasMoreRecords.value = false
                return
            }
            val filesToLoad = listFiles.subList(startIndex, endExclusive).toList()
            val latestFile = latestListFile
            val dischargeDisplayPositive = listDischargeDisplayPositive
            val nextPageRecords = withContext(Dispatchers.IO) {
                filesToLoad.mapNotNull { file ->
                    // 与原策略一致：非最新文件优先读缓存，减少 CSV 解析成本。
                    HistoryRepository.loadStats(context, file, file != latestFile)
                        ?.let { record ->
                            mapHistoryRecordForDisplay(record, dischargeDisplayPositive)
                        }
                }
            }
            // I/O 返回后再次校验 token，避免旧任务覆盖新列表状态。
            if (token != listLoadToken) return
            if (nextPageRecords.isNotEmpty()) {
                _records.value += nextPageRecords
            }
            loadedRecordCount = endExclusive
            _hasMoreRecords.value = loadedRecordCount < listFiles.size
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
                    _recordPoints.value = emptyList()
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
                _recordPoints.value = points
                recomputeRecordChartUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "加载记录详情失败: ${recordsFile.name}", e)
                _userMessage.value = "加载记录详情失败"
                _recordDetail.value = null
                _recordPoints.value = emptyList()
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
                    _records.value = _records.value.filter { it.asRecordsFile() != recordsFile }
                    if (currentListType == recordsFile.type) {
                        // 删除后同步修正分页数据源与游标，避免 hasMore 状态失真。
                        listFiles = listFiles.filter { it.name != recordsFile.name }
                        loadedRecordCount = loadedRecordCount.coerceAtMost(listFiles.size)
                        _hasMoreRecords.value = loadedRecordCount < listFiles.size
                    }
                    val detail = _recordDetail.value
                    if (detail != null && detail.asRecordsFile() == recordsFile) {
                        _recordDetail.value = null
                        _recordPoints.value = emptyList()
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
                // 导出失败只做可见提示并记录日志，不向上抛出导致界面崩溃
                Log.e(TAG, "导出失败: ${recordsFile.name}", e)
                _userMessage.value = "导出失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    fun updatePowerDisplayConfig(dualCellEnabled: Boolean, calibrationValue: Int) {
        if (_dualCellEnabled.value == dualCellEnabled && _calibrationValue.value == calibrationValue) {
            return
        }
        _dualCellEnabled.value = dualCellEnabled
        _calibrationValue.value = calibrationValue
        recomputeRecordChartUiState()
    }

    private fun recomputeRecordChartUiState() {
        val displayPoints = mapDisplayPoints(
            detailType = _recordDetail.value?.type,
            rawPoints = _recordPoints.value,
            dualCellEnabled = _dualCellEnabled.value,
            calibrationValue = _calibrationValue.value
        )
        _recordChartUiState.value = computeViewportState(displayPoints)
    }

    private fun mapDisplayPoints(
        detailType: BatteryStatus?,
        rawPoints: List<ChartPoint>,
        dualCellEnabled: Boolean,
        calibrationValue: Int
    ): List<ChartPoint> {
        return rawPoints.map { point ->
            val displayPowerW = computePowerW(
                rawPower = point.power,
                dualCellEnabled = dualCellEnabled,
                calibrationValue = calibrationValue
            )
            val powerForChart = if (detailType == BatteryStatus.Charging && displayPowerW < 0) {
                0.0
            } else {
                displayPowerW
            }
            point.copy(power = powerForChart)
        }
    }

    private fun computeViewportState(displayPoints: List<ChartPoint>): RecordDetailChartUiState {
        val minChartTime = displayPoints.minOfOrNull { it.timestamp }
        val maxChartTime = displayPoints.maxOfOrNull { it.timestamp }
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
            displayPoints = displayPoints,
            minChartTime = minChartTime,
            maxChartTime = maxChartTime,
            maxViewportStartTime = maxViewportStart,
            viewportDurationMs = viewportDurationMs
        )
    }
}
