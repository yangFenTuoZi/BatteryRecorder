package yangfentuozi.batteryrecorder.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 将毫秒时长格式化为小时字符串
 * @param durationMs 时长（毫秒），如 7200000 表示 2 小时
 * @return 格式化后的字符串，如 "2.0h"
 */
fun formatDurationHours(durationMs: Long): String {
    // 1小时 = 3600000毫秒，转换为小数小时数
    val hours = durationMs / 3600000.0
    return String.format(Locale.getDefault(), "%.1fh", hours)
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
    // cellMultiplier: 双电芯为2，单电芯为1
    // 纳瓦转瓦特: ÷ 1000000000
    val finalValue = (if (dualCellEnabled) 2 else 1) * calibrationValue * (powerW / 1000000000)
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
    val finalValue = (if (dualCellEnabled) 2 else 1) * calibrationValue * (powerW / 1000000000)
    return String.format(Locale.getDefault(), "%.0f W", finalValue)
}
