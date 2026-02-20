package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.HistoryRepository
import yangfentuozi.batteryrecorder.data.history.HistorySummary
import yangfentuozi.batteryrecorder.data.history.RecordType
import yangfentuozi.batteryrecorder.data.history.SyncUtil
import yangfentuozi.batteryrecorder.ipc.Service

class MainViewModel : ViewModel() {
    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _showStopDialog = MutableStateFlow(false)
    val showStopDialog: StateFlow<Boolean> = _showStopDialog.asStateFlow()

    private val _showAboutDialog = MutableStateFlow(false)
    val showAboutDialog: StateFlow<Boolean> = _showAboutDialog.asStateFlow()

    private val _chargeSummary = MutableStateFlow<HistorySummary?>(null)
    val chargeSummary: StateFlow<HistorySummary?> = _chargeSummary.asStateFlow()

    private val _dischargeSummary = MutableStateFlow<HistorySummary?>(null)
    val dischargeSummary: StateFlow<HistorySummary?> = _dischargeSummary.asStateFlow()

    private val _currentRecord = MutableStateFlow<HistoryRecord?>(null)
    val currentRecord: StateFlow<HistoryRecord?> = _currentRecord.asStateFlow()

    private val _isLoadingStats = MutableStateFlow(false)
    val isLoadingStats: StateFlow<Boolean> = _isLoadingStats.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val serviceListener = object : Service.ServiceConnection {
        override fun onServiceConnected() {
            mainHandler.post {
                _serviceConnected.value = true
            }
        }

        override fun onServiceDisconnected() {
            mainHandler.post {
                _serviceConnected.value = false
            }
        }
    }

    init {
        Service.addListener(serviceListener)
        _serviceConnected.value = Service.service != null
    }

    override fun onCleared() {
        Service.removeListener(serviceListener)
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun showStopDialog() {
        _showStopDialog.value = true
    }

    fun dismissStopDialog() {
        _showStopDialog.value = false
    }

    fun stopService() {
        Thread {
            Service.service?.stopService()
        }.start()
    }

    fun showAboutDialog() {
        _showAboutDialog.value = true
    }

    fun dismissAboutDialog() {
        _showAboutDialog.value = false
    }

    fun loadStatistics(context: Context) {
        if (_isLoadingStats.value) return

        viewModelScope.launch {
            _isLoadingStats.value = true
            try {
                val dischargeDisplayPositive = getDischargeDisplayPositive(context)
                _chargeSummary.value = HistoryRepository.loadSummary(context, RecordType.CHARGE)
                    ?.let { mapHistorySummaryForDisplay(it, dischargeDisplayPositive) }
                _dischargeSummary.value = HistoryRepository.loadSummary(context, RecordType.DISCHARGE)
                    ?.let { mapHistorySummaryForDisplay(it, dischargeDisplayPositive) }
                _currentRecord.value = loadLatestRecordForDisplay(context, dischargeDisplayPositive)
            } finally {
                _isLoadingStats.value = false
            }
        }
    }

    fun refreshStatistics(context: Context) {
        _chargeSummary.value = null
        _dischargeSummary.value = null
        _currentRecord.value = null
        loadStatistics(context)
    }

    fun refreshCurrentRecord(context: Context) {
        viewModelScope.launch {
            val dischargeDisplayPositive = getDischargeDisplayPositive(context)
            _currentRecord.value = loadLatestRecordForDisplay(context, dischargeDisplayPositive)
        }
    }

    suspend fun onLiveStatusChanged(context: Context, liveStatus: Int?, intervalMs: Long) {
        if (liveStatus == null) return

        withContext(Dispatchers.IO) {
            runCatching { SyncUtil.sync(context) }
        }

        val dischargeDisplayPositive = getDischargeDisplayPositive(context)
        val before = _currentRecord.value
        delay((intervalMs * 2).coerceAtLeast(800L))

        repeat(3) {
            _currentRecord.value = loadLatestRecordForDisplay(context, dischargeDisplayPositive)
            delay(350L)
            val after = _currentRecord.value
            if (after?.name != before?.name || after?.type != before?.type) return
        }
    }

    private suspend fun loadLatestRecordForDisplay(
        context: Context,
        dischargeDisplayPositive: Boolean
    ): HistoryRecord? {
        return withContext(Dispatchers.IO) {
            HistoryRepository.loadLatestRecord(context)
        }?.let { mapHistoryRecordForDisplay(it, dischargeDisplayPositive) }
    }
}
