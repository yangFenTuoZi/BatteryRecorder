package yangfentuozi.batteryrecorder.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NW_TO_WATT = 1_000_000_000.0

/**
 * 将毫秒时长格式化为小时分钟字符串
 * @param durationMs 时长（毫秒），如 7200000 表示 2 小时
 * @return 格式化后的字符串，如 "2h0m"；小时为0时只显示分钟，如 "30m"
 */
fun formatDurationHours(durationMs: Long): String {
    val totalMinutes = durationMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
}

/**
 * 将时间戳格式化为 HH:mm 格式
 * @param timestamp Unix 时间戳（毫秒），如 1705900800000
 * @return 格式化后的时间字符串，如 "14:30"
 */
fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun formatRelativeTime(offsetMs: Long): String {
    val totalMinutes = (offsetMs / 60000L).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        if (minutes == 0) {
            "${hours}h"
        } else {
            "${hours}h${minutes}m"
        }
    } else {
        "${minutes}m"
    }
}

fun computePowerW(
    rawPowerNw: Double,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): Double {
    val cellMultiplier = if (dualCellEnabled) 2 else 1
    return cellMultiplier * calibrationValue * (rawPowerNw / NW_TO_WATT)
}

/**
 * 将原始功率值转换为瓦特并格式化（保留1位小数）
 *
 * 计算公式：finalValue = cellMultiplier × calibrationValue × (powerW / 1000000000)
 * - 原始值单位为纳瓦(nW)，除以 10^9 转换为瓦特(W)
 * - 双电芯设备需乘以 2（两块电池并联）
 * - calibrationValue 用于校准不同设备的测量误差
 *
 * @param powerW 原始功率值（纳瓦 nW），从 /sys/class/power_supply/battery/power_now 读取
 * @param dualCellEnabled 是否为双电芯设备，true 时功率值乘以 2
 * @param calibrationValue 校准系数，用于修正设备测量偏差，通常为 1
 * @return 格式化后的功率字符串，如 "12.5 W"
 */
fun formatPower(
    powerW: Double,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    val finalValue = computePowerW(powerW, dualCellEnabled, calibrationValue)
    return String.format(Locale.getDefault(), "%.2f W", finalValue)
}

/**
 * 将原始功率值转换为瓦特并格式化（取整，无小数）
 *
 * 计算逻辑同 [formatPower]，区别在于输出为整数
 *
 * @param powerW 原始功率值（纳瓦 nW）
 * @param dualCellEnabled 是否为双电芯设备
 * @param calibrationValue 校准系数
 * @return 格式化后的功率字符串，如 "12 W"
 */
fun formatPowerInt(
    powerW: Double,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    val finalValue = computePowerW(powerW, dualCellEnabled, calibrationValue)
    return String.format(Locale.getDefault(), "%.0f W", finalValue)
}
