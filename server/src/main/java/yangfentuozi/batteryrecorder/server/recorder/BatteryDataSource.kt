package yangfentuozi.batteryrecorder.server.recorder

interface BatteryDataSource {
    fun readSample(nowMs: Long): BatterySample?
}
