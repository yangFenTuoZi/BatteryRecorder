package yangfentuozi.batteryrecorder.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDurationHours(durationMs: Long): String {
    val hours = durationMs / 3600000.0
    return String.format(Locale.getDefault(), "%.1fh", hours)
}

fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun formatPower(
    powerW: Double,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    val finalValue = (if (dualCellEnabled) 2 else 1) * calibrationValue * (powerW / 1000000)
    return String.format(Locale.getDefault(), "%.1f W", finalValue)
}

fun formatPowerInt(
    powerW: Double,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    val finalValue = (if (dualCellEnabled) 2 else 1) * calibrationValue * (powerW / 1000000)
    return String.format(Locale.getDefault(), "%.0f W", finalValue)
}
