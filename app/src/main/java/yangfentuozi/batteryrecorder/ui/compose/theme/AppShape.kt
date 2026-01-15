package yangfentuozi.batteryrecorder.ui.compose.theme

import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle

/**
 * 应用统一圆角形状定义
 * 使用 ContinuousRoundedRectangle 实现平滑圆角
 */
object AppShape {
    // 基础圆角大小
    val extraSmall = ContinuousRoundedRectangle(6.dp)
    val small = ContinuousRoundedRectangle(8.dp)
    val medium = ContinuousRoundedRectangle(12.dp)
    val large = ContinuousRoundedRectangle(16.dp)
    val extraLarge = ContinuousRoundedRectangle(24.dp)

    // 按钮组专用形状（Material 3 Expressive 设计）
    object ButtonGrid {
        val topStart = ContinuousRoundedRectangle(
            topStart = 24.dp,
            topEnd = 6.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp
        )
        val topEnd = ContinuousRoundedRectangle(
            topStart = 6.dp,
            topEnd = 24.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp
        )
        val bottomStart = ContinuousRoundedRectangle(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 24.dp,
            bottomEnd = 6.dp
        )
        val bottomEnd = ContinuousRoundedRectangle(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 6.dp,
            bottomEnd = 24.dp
        )
    }

    // 拼接列组专用形状
    object SplicedGroup {
        val single = ContinuousRoundedRectangle(16.dp)
        val top = ContinuousRoundedRectangle(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp
        )
        val bottom = ContinuousRoundedRectangle(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        )
        val middle = ContinuousRoundedRectangle(6.dp)

        // Home 页面启动卡片专用（顶部项，下方是双列）
        val homeStartCard = ContinuousRoundedRectangle(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp
        )
    }

    // 拼接行组专用形状
    object SplicedRow {
        val single = ContinuousRoundedRectangle(16.dp)
        val start = ContinuousRoundedRectangle(
            topStart = 16.dp,
            topEnd = 6.dp,
            bottomStart = 16.dp,
            bottomEnd = 6.dp
        )
        val end = ContinuousRoundedRectangle(
            topStart = 6.dp,
            topEnd = 16.dp,
            bottomStart = 6.dp,
            bottomEnd = 16.dp
        )
        val middle = ContinuousRoundedRectangle(6.dp)

        // Home 页面专用形状
        val homeChargeStats = ContinuousRoundedRectangle(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 16.dp,
            bottomEnd = 6.dp
        )
        val homeDischargeStats = ContinuousRoundedRectangle(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 6.dp,
            bottomEnd = 16.dp
        )
    }
}
