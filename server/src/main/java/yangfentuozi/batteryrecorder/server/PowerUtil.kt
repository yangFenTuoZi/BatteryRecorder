package yangfentuozi.batteryrecorder.server

import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

object PowerUtil {
    private const val VOLTAGE_PATH = "/sys/class/power_supply/battery/voltage_now"
    private const val CURRENT_PATH = "/sys/class/power_supply/battery/current_now"
    private const val CAPACITY_PATH = "/sys/class/power_supply/battery/capacity"
    private const val STATUS_PATH = "/sys/class/power_supply/battery/status"

    @Throws(IOException::class)
    private fun readLine(path: String?): String {
        BufferedReader(FileReader(path)).use { br ->
            return br.readLine().trim { it <= ' ' }
        }
    }

    @get:Throws(IOException::class)
    private val voltage: Long
        get() = readLine(VOLTAGE_PATH).toLongOrNull() ?: 0

    @get:Throws(IOException::class)
    private val current: Long
        get() = readLine(CURRENT_PATH).toLongOrNull() ?: 0

    @get:Throws(IOException::class)
    val power: Long
        get() = voltage * current

    @get:Throws(IOException::class)
    val capacity: Int
        get() = readLine(CAPACITY_PATH).toIntOrNull() ?: 0

    @get:Throws(IOException::class)
    val status: BatteryStatus
        get() = when(readLine(STATUS_PATH)) {
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
