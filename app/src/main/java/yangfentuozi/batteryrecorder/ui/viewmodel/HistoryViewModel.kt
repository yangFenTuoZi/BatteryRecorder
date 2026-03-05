package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private companion object {
        const val FIRST_BATCH_SIZE = 30
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
                val latestFile = files.firstOrNull()

                // 首批秒出
                val firstBatch = withContext(Dispatchers.IO) {
                    files.take(FIRST_BATCH_SIZE).mapNotNull {
                        HistoryRepository.loadStats(context, it, it != latestFile)
                            ?.let { r -> mapHistoryRecordForDisplay(r, dischargeDisplayPositive) }
                    }
                }
                _records.value = firstBatch

                // 剩余批次追加
                val remaining = files.drop(FIRST_BATCH_SIZE)
                if (remaining.isNotEmpty()) {
                    val rest = withContext(Dispatchers.IO) {
                        remaining.mapNotNull {
                            HistoryRepository.loadStats(context, it, it != latestFile)
                                ?.let { r -> mapHistoryRecordForDisplay(r, dischargeDisplayPositive) }
                        }
                    }
                    _records.value = firstBatch + rest
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadRecord(context: Context, recordsFile: RecordsFile) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dischargeDisplayPositive = getDischargeDisplayPositive(context)
                val (detail, points) = withContext(Dispatchers.IO) {
                    val recordFile = recordsFile.toFile(context)
                    val detail = recordFile
                        ?.let { HistoryRepository.loadRecord(context, it) }
                        ?.let { mapHistoryRecordForDisplay(it, dischargeDisplayPositive) }
                    val points = if (recordFile != null) {
                        mapChartPointsForDisplay(
                            points = HistoryRepository.loadRecordPoints(context, recordsFile),
                            batteryStatus = recordsFile.type,
                            dischargeDisplayPositive = dischargeDisplayPositive
                        )
                    } else {
                        emptyList()
                    }
                    detail to points
                }
                _recordDetail.value = detail
                _recordPoints.value = points
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
            } catch (t: Throwable) {
                // 导出失败只做可见提示并记录日志，不向上抛出导致界面崩溃
                Log.e(TAG, "导出失败: ${recordsFile.name}", t)
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
        val detail = _recordDetail.value
        val rawPoints = _recordPoints.value
        val dualCellEnabled = _dualCellEnabled.value
        val calibrationValue = _calibrationValue.value

        val displayPoints = rawPoints.map { point ->
            val displayPowerW = computePowerW(
                rawPower = point.power,
                dualCellEnabled = dualCellEnabled,
                calibrationValue = calibrationValue
            )
            val powerForChart = if (detail?.type == BatteryStatus.Charging && displayPowerW < 0) {
                0.0
            } else {
                displayPowerW
            }
            point.copy(power = powerForChart)
        }

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

        _recordChartUiState.value = RecordDetailChartUiState(
            displayPoints = displayPoints,
            minChartTime = minChartTime,
            maxChartTime = maxChartTime,
            maxViewportStartTime = maxViewportStart,
            viewportDurationMs = viewportDurationMs
        )
    }
}
