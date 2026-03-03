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

class LiveRecordViewModel : ViewModel() {
    private val _livePoints = MutableStateFlow<List<Long>>(emptyList())
    val livePoints: StateFlow<List<Long>> = _livePoints.asStateFlow()

    private var _lastStatus = MutableStateFlow(BatteryStatus.Unknown)
    val lastStatus: StateFlow<BatteryStatus> = _lastStatus.asStateFlow()

    private var _lastTemp = MutableStateFlow(0)
    val lastTemp: StateFlow<Int> = _lastTemp.asStateFlow()

    private val buffer = ArrayList<Long>(MAX_LIVE_POINTS + 1)

    fun handleRecord(power: Long, status: BatteryStatus, temp: Int) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val lastStatusValue = _lastStatus.value
            if (lastStatusValue != status) {
                buffer.clear()
            }

            buffer.add(power)
            while (buffer.size > MAX_LIVE_POINTS) {
                buffer.removeAt(0)
            }

            _lastTemp.value = temp
            _lastStatus.value = status
            _livePoints.value = buffer.toList()
        }
    }
}
