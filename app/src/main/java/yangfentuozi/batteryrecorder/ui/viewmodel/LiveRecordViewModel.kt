package yangfentuozi.batteryrecorder.ui.viewmodel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.server.IRecordListener

private const val MAX_LIVE_POINTS = 20
private const val DEFAULT_INTERVAL_MS = 1000L

data class LivePowerPoint(
    val timestamp: Long,
    val powerNw: Long,
    val status: Int,
    val isGap: Boolean = false
)

class LiveRecordViewModel : ViewModel() {
    private val _livePoints = MutableStateFlow<List<LivePowerPoint>>(emptyList())
    val livePoints: StateFlow<List<LivePowerPoint>> = _livePoints.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var intervalMs: Long = DEFAULT_INTERVAL_MS
    private var lastTimestamp: Long? = null
    private var lastPowerNw: Long? = null
    private var lastStatus: Int? = null
    private var isListenerRegistered = false
    private val buffer = ArrayList<LivePowerPoint>(MAX_LIVE_POINTS + 1)

    private val listener = object : IRecordListener.Stub() {
        override fun onRecord(timestamp: Long, power: Long, status: Int) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                handleRecord(timestamp, power, status)
            }
        }
    }

    private val serviceListener = object : Service.ServiceConnection {
        override fun onServiceConnected() {
            mainHandler.post {
                registerListenerIfNeeded()
            }
        }

        override fun onServiceDisconnected() {
            mainHandler.post {
                unregisterListener()
            }
        }
    }

    init {
        Service.addListener(serviceListener)
        if (Service.service != null) {
            registerListenerIfNeeded()
        }
    }

    override fun onCleared() {
        unregisterListener()
        Service.removeListener(serviceListener)
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun updateIntervalMs(value: Long) {
        intervalMs = value.coerceAtLeast(1L)
    }

    private fun registerListenerIfNeeded() {
        if (isListenerRegistered) return
        Service.service?.registerRecordListener(listener)
        isListenerRegistered = Service.service != null
    }

    private fun unregisterListener() {
        if (!isListenerRegistered) return
        Service.service?.unregisterRecordListener(listener)
        isListenerRegistered = false
    }

    private fun handleRecord(timestamp: Long, power: Long, status: Int) {
        val lastStatusValue = lastStatus
        if (lastStatusValue != null && lastStatusValue != status) {
            buffer.clear()
            lastTimestamp = null
            lastPowerNw = null
        }

        val last = lastTimestamp
        val resetThresholdMs = intervalMs * 2
        if (last != null && timestamp - last > resetThresholdMs && lastStatusValue == status) {
            val gapTimestamp = last + (timestamp - last) / 2
            val gapPower = lastPowerNw?.let { (it + power) / 2 } ?: power
            buffer.add(LivePowerPoint(gapTimestamp, gapPower, status, isGap = true))
        }

        buffer.add(LivePowerPoint(timestamp, power, status))
        while (buffer.size > MAX_LIVE_POINTS) {
            buffer.removeAt(0)
        }

        lastTimestamp = timestamp
        lastPowerNw = power
        lastStatus = status
        _livePoints.value = buffer.toList()
    }
}
