package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.HistoryRepository
import yangfentuozi.batteryrecorder.data.history.HistoryRepository.toFile
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
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

    fun loadRecords(context: Context, type: BatteryStatus) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dischargeDisplayPositive = getDischargeDisplayPositive(context)
                _records.value = HistoryRepository.loadRecords(context, type)
                    .map { mapHistoryRecordForDisplay(it, dischargeDisplayPositive) }
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
                val recordFile = recordsFile.toFile(context)
                _recordDetail.value = recordFile
                    ?.let { HistoryRepository.loadRecord(context, it) }
                    ?.let { mapHistoryRecordForDisplay(it, dischargeDisplayPositive) }
                _recordPoints.value = if (recordFile != null) {
                    mapChartPointsForDisplay(
                        points = HistoryRepository.loadRecordPoints(context, recordsFile),
                        batteryStatus = recordsFile.type,
                        dischargeDisplayPositive = dischargeDisplayPositive
                    )
                } else {
                    emptyList()
                }
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
                val deleted = HistoryRepository.deleteRecord(context, recordsFile)
                if (deleted) {
                    val dischargeDisplayPositive = getDischargeDisplayPositive(context)
                    _records.value = HistoryRepository.loadRecords(context, recordsFile.type)
                        .map { mapHistoryRecordForDisplay(it, dischargeDisplayPositive) }
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
                rawPowerNw = point.power,
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
