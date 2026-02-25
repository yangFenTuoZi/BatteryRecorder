package yangfentuozi.batteryrecorder.shared.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import yangfentuozi.batteryrecorder.shared.Constants

@Parcelize
enum class BatteryStatus(val value: Int) : Parcelable {
    Charging(0),
    Discharging(1),
    Full(2);

    companion object {
        fun fromValue(value: Int): BatteryStatus? =
            entries.find { it.value == value }

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
            Full -> throw IllegalStateException()
        }
}