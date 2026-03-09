package yangfentuozi.batteryrecorder.data.model

/**
 * 记录详情图表统一使用的数据模型。
 *
 * 设计上同时承载两类点：
 * 1. 原始展示点：来自记录文件逐点换算后的真实采样值。
 * 2. 趋势点：按时间分桶后得到的低频代表点。
 *
 * 因此同一个结构里同时保留 rawPowerW / fittedPowerW：
 * - 原始点通常满足 fittedPowerW == rawPowerW
 * - 趋势点则使用桶内中位数覆盖 fittedPowerW
 *
 * 这样图表层可以在“原始 / 趋势”两种模式下复用同一套坐标与交互逻辑，
 * 避免再引入额外的数据类型和分支。
 */
data class RecordDetailChartPoint(
    val timestamp: Long,
    val rawPowerW: Double,
    val fittedPowerW: Double,
    val capacity: Int,
    val isDisplayOn: Boolean,
    val temp: Int,
)

/**
 * 对记录详情图表点应用“孤立息屏点过滤”。
 *
 * 该规则只服务“关闭息屏记录显示”这一展示语义：
 * - 如果一个息屏点前后都仍是亮屏点，则把它视为切换抖动或过渡点
 * - 真正连续的息屏区间不会被这个规则删除
 *
 * 这里返回新列表而不修改原始集合，便于调用方分别保留：
 * - 原始序列
 * - 过滤后的展示序列
 */
internal fun normalizeRecordDetailChartPoints(
    points: List<RecordDetailChartPoint>,
    recordScreenOffEnabled: Boolean
): List<RecordDetailChartPoint> {
    if (recordScreenOffEnabled) return points
    if (points.size < 3) return points

    val sorted = points.sortedBy { it.timestamp }
    val result = ArrayList<RecordDetailChartPoint>(sorted.size)
    for (index in sorted.indices) {
        val current = sorted[index]
        if (!current.isDisplayOn) {
            val previous = sorted.getOrNull(index - 1)
            val next = sorted.getOrNull(index + 1)
            if (previous?.isDisplayOn == true && next?.isDisplayOn == true) {
                continue
            }
        }
        result += current
    }
    return result
}
