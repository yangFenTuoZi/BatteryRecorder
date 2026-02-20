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
import yangfentuozi.batteryrecorder.data.history.RecordType
import yangfentuozi.batteryrecorder.data.model.ChartPoint

class HistoryViewModel : ViewModel() {
    private val _records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val records: StateFlow<List<HistoryRecord>> = _records.asStateFlow()

    private val _recordDetail = MutableStateFlow<HistoryRecord?>(null)
    val recordDetail: StateFlow<HistoryRecord?> = _recordDetail.asStateFlow()

    private val _recordPoints = MutableStateFlow<List<ChartPoint>>(emptyList())
    val recordPoints: StateFlow<List<ChartPoint>> = _recordPoints.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadRecords(context: Context, type: RecordType) {
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

    fun loadRecord(context: Context, type: RecordType, name: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dischargeDisplayPositive = getDischargeDisplayPositive(context)
                _recordDetail.value = HistoryRepository.loadRecord(context, type, name)
                    ?.let { mapHistoryRecordForDisplay(it, dischargeDisplayPositive) }
                _recordPoints.value = mapChartPointsForDisplay(
                    points = HistoryRepository.loadRecordPoints(context, type, name),
                    recordType = type,
                    dischargeDisplayPositive = dischargeDisplayPositive
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteRecord(context: Context, type: RecordType, name: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deleted = HistoryRepository.deleteRecord(context, type, name)
                if (deleted) {
                    val dischargeDisplayPositive = getDischargeDisplayPositive(context)
                    _records.value = HistoryRepository.loadRecords(context, type)
                        .map { mapHistoryRecordForDisplay(it, dischargeDisplayPositive) }
                    val detail = _recordDetail.value
                    if (detail != null && detail.type == type && detail.name == name) {
                        _recordDetail.value = null
                        _recordPoints.value = emptyList()
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
