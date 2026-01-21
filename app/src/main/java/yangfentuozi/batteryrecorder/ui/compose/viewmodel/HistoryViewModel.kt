package yangfentuozi.batteryrecorder.ui.compose.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.ui.compose.components.charts.ChartPoint
import yangfentuozi.batteryrecorder.util.HistoryRecord
import yangfentuozi.batteryrecorder.util.HistoryRepository
import yangfentuozi.batteryrecorder.util.RecordType

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
                _records.value = HistoryRepository.loadRecords(context, type)
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
                _recordDetail.value = HistoryRepository.loadRecord(context, type, name)
                _recordPoints.value = HistoryRepository.loadRecordPoints(context, type, name)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
