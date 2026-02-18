package yangfentuozi.batteryrecorder.server.recorder

class StartupLockedBatteryDataSource(
    mode: Mode,
    private val sysDataSource: BatteryDataSource,
    private val batteryManagerDataSource: BatteryDataSource
) : BatteryDataSource {

    enum class Mode {
        SYS_LOCKED,
        BM_LOCKED
    }

    private val lockedDataSource: BatteryDataSource = when (mode) {
        Mode.SYS_LOCKED -> sysDataSource
        Mode.BM_LOCKED -> batteryManagerDataSource
    }

    override fun readSample(nowMs: Long): BatterySample? {
        return lockedDataSource.readSample(nowMs)
    }
}
