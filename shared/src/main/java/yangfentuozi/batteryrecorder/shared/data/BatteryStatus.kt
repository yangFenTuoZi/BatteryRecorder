package yangfentuozi.batteryrecorder.shared.data

import android.os.BatteryManager
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import yangfentuozi.batteryrecorder.shared.Constants

@Parcelize
enum class BatteryStatus(val value: Int) : Parcelable {
    Unknown(BatteryManager.BATTERY_STATUS_UNKNOWN),
    Charging(BatteryManager.BATTERY_STATUS_CHARGING),
    Discharging(BatteryManager.BATTERY_STATUS_DISCHARGING),
    NotCharging(BatteryManager.BATTERY_STATUS_NOT_CHARGING),
    Full(BatteryManager.BATTERY_STATUS_FULL);

    companion object {
        fun fromValue(value: Int): BatteryStatus =
            entries.find { it.value == value } ?: Unknown

        fun fromDataDirName(dataDirName: String?): BatteryStatus = when (dataDirName) {
            Constants.CHARGE_DATA_DIR -> Charging
            Constants.DISCHARGE_DATA_DIR -> Discharging
            else -> throw IllegalArgumentException()
        }
    }

    val dataDirName: String
        get() = when (this) {
            Charging -> Constants.CHARGE_DATA_DIR
            Discharging -> Constants.DISCHARGE_DATA_DIR
            else -> throw IllegalStateException()
        }
}