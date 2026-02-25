package yangfentuozi.batteryrecorder.shared.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LineRecord(
    val timestamp: Long, val power: Long, val packageName: String?,
    val capacity: Int, val isDisplayOn: Int, val status: BatteryStatus?,
    val temp: Int
) : Parcelable {
    override fun toString(): String {
        return "$timestamp,$power,$packageName,$capacity,$isDisplayOn,$temp"
    }

    companion object {
        fun fromString(line: String) : LineRecord? {
            val parts = line.split(",")
            if (parts.size < 6) return null

            val timestamp = parts[0].toLongOrNull() ?: return null
            val power = parts[1].toLongOrNull() ?: return null
            val packageName = parts[2]
            val capacity = parts[3].toIntOrNull() ?: return null
            val isDisplayOn = parts[4].toIntOrNull() ?: return null

            val temp = if (parts.size > 5) parts[5].toIntOrNull() ?: return null else 0

            return LineRecord(
                timestamp, power, packageName, capacity, isDisplayOn, null, temp
            )
        }
    }
}
