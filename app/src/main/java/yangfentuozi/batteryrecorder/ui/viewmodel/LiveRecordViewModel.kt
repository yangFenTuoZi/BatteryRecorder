package yangfentuozi.batteryrecorder.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus

private const val MAX_LIVE_POINTS = 20

data class LivePowerPoint(
    val powerRaw: Long,
    val status: BatteryStatus,
    val temp: Int = 0
)

class LiveRecordViewModel : ViewModel() {
    private val _livePoints = MutableStateFlow<List<LivePowerPoint>>(emptyList())
    val livePoints: StateFlow<List<LivePowerPoint>> = _livePoints.asStateFlow()

    private var lastStatus: BatteryStatus? = null
    private val buffer = ArrayList<LivePowerPoint>(MAX_LIVE_POINTS + 1)

    fun handleRecord(power: Long, status: BatteryStatus, temp: Int) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val lastStatusValue = lastStatus
            if (lastStatusValue != null && lastStatusValue != status) {
                buffer.clear()
            }

            buffer.add(LivePowerPoint(power, status, temp))
            while (buffer.size > MAX_LIVE_POINTS) {
                buffer.removeAt(0)
            }

            lastStatus = status
            _livePoints.value = buffer.toList()
        }
    }
}
