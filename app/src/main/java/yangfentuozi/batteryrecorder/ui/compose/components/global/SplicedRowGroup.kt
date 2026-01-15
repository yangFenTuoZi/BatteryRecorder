package yangfentuozi.batteryrecorder.ui.compose.components.global

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.compose.theme.AppShape

/**
 * 拼接行分组组件
 *
 * 专为两个项目设计的水平布局，自动处理圆角：
 * - 左侧项：左侧 16.dp 圆角，右侧 6.dp 圆角
 * - 右侧项：左侧 6.dp 圆角，右侧 16.dp 圆角
 * - 项目间距：2.dp
 *
 * 注意：此组件仅接受两个 item
 */
@Composable
fun SplicedRowGroup(
    modifier: Modifier = Modifier,
    content: SplicedGroupScope.() -> Unit
) {
    val scope = SplicedGroupScope().apply(content)
    val visibleItems = scope.items.filter { it.visible }

    require(visibleItems.size == 2) {
        "SplicedRowGroup 只接受两个 item，当前有 ${visibleItems.size} 个"
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 左侧项（充电统计）
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(AppShape.SplicedRow.homeChargeStats)
                .background(MaterialTheme.colorScheme.surfaceBright)
        ) {
            visibleItems[0].content()
        }

        // 右侧项（放电统计）
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(AppShape.SplicedRow.homeDischargeStats)
                .background(MaterialTheme.colorScheme.surfaceBright)
        ) {
            visibleItems[1].content()
        }
    }
}
