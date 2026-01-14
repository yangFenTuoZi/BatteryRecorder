package yangfentuozi.batteryrecorder.server

import java.io.IOException

object PowerUtil {

    @JvmStatic
    external fun nativeInit(): Int

    @JvmStatic
    external fun nativeGetVoltage(): Long

    @JvmStatic
    external fun nativeGetCurrent(): Long

    @JvmStatic
    external fun nativeGetCapacity(): Int

    @JvmStatic
    external fun nativeGetStatus(): String

    @get:Throws(IOException::class)
    val power: Long
        get() = nativeGetVoltage() * nativeGetCurrent()

    @get:Throws(IOException::class)
    val capacity: Int
        get() = nativeGetCapacity()

    @get:Throws(IOException::class)
    val status: BatteryStatus
        get() = when(nativeGetStatus()) {
            "Charging" -> BatteryStatus.Charging
            "Discharging" -> BatteryStatus.Discharging
            else -> BatteryStatus.Full
        }

    enum class BatteryStatus {
        Charging,
        Discharging,
        Full
    }
}
