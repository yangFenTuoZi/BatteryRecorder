package yangfentuozi.batteryrecorder.server.recorder.sampler

import yangfentuozi.batteryrecorder.shared.data.BatteryStatus

interface Sampler {
    fun sample(): BatteryData

    data class BatteryData(
        val voltage: Long,
        val current: Long,
        val capacity: Int,
        val status: BatteryStatus,
        val temp: Int
    )
}