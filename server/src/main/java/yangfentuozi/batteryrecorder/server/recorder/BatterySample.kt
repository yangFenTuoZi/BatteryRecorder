package yangfentuozi.batteryrecorder.server.recorder

import yangfentuozi.batteryrecorder.server.data.BatteryStatus

data class BatterySample(
    val timestampMs: Long,
    val powerNw: Long,
    val capacity: Int,
    val status: BatteryStatus
)
