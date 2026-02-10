package yangfentuozi.batteryrecorder.server.recorder

import yangfentuozi.batteryrecorder.server.data.BatteryStatus
import java.io.IOException

object Native {

    @JvmStatic
    external fun nativeInit(): Int

    @JvmStatic
    external fun nativeGetVoltage(): Long

    @JvmStatic
    external fun nativeGetCurrent(): Long

    @JvmStatic
    external fun nativeGetCapacity(): Int

    @JvmStatic
    external fun nativeGetStatus(): Int

    @get:Throws(IOException::class)
    val power: Long
        get() = nativeGetVoltage() * nativeGetCurrent()

    @get:Throws(IOException::class)
    val capacity: Int
        get() = nativeGetCapacity()

    @get:Throws(IOException::class)
    val status: BatteryStatus
        get() = when(nativeGetStatus().toChar()) {
            'C' -> BatteryStatus.Charging
            'D' -> BatteryStatus.Discharging
            else -> BatteryStatus.Full
        }
}