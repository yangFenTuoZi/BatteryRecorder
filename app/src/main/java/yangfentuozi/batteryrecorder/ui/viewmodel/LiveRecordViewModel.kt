package yangfentuozi.batteryrecorder.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus

private const val MAX_LIVE_POINTS = 20

data class LivePowerPoint(
    val timestamp: Long,
    val powerRaw: Long,
    val status: BatteryStatus,
    val temp: Int = 0,
    val isGap: Boolean = false
)

class LiveRecordViewModel : ViewModel() {
    private val _livePoints = MutableStateFlow<List<LivePowerPoint>>(emptyList())
    val livePoints: StateFlow<List<LivePowerPoint>> = _livePoints.asStateFlow()

    private var intervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS
    private var lastTimestamp: Long? = null
    private var lastPowerRaw: Long? = null
    private var lastStatus: BatteryStatus? = null
    private val buffer = ArrayList<LivePowerPoint>(MAX_LIVE_POINTS + 1)

    fun updateIntervalMs(value: Long) {
        intervalMs = value.coerceAtLeast(1L)
    }

    fun handleRecord(timestamp: Long, power: Long, status: BatteryStatus, temp: Int) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val lastStatusValue = lastStatus
            if (lastStatusValue != null && lastStatusValue != status) {
                buffer.clear()
                lastTimestamp = null
                lastPowerRaw = null
            }

            val last = lastTimestamp
            val resetThresholdMs = intervalMs * 2
            if (last != null && timestamp - last > resetThresholdMs && lastStatusValue == status) {
                val gapTimestamp = last + (timestamp - last) / 2
                val gapPower = lastPowerRaw?.let { (it + power) / 2 } ?: power
                buffer.add(LivePowerPoint(gapTimestamp, gapPower, status, temp, isGap = true))
            }

            buffer.add(LivePowerPoint(timestamp, power, status, temp))
            while (buffer.size > MAX_LIVE_POINTS) {
                buffer.removeAt(0)
            }

            lastTimestamp = timestamp
            lastPowerRaw = power
            lastStatus = status
            _livePoints.value = buffer.toList()
        }
    }
}
