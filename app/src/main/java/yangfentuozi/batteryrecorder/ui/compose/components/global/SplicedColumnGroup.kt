package yangfentuozi.batteryrecorder.ui.compose.components.global

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.compose.theme.AppShape

/**
 * 拼接列分组组件
 *
 * 实现多个设置项的垂直堆叠，自动处理不同位置的圆角：
 * - 单项：四角 16.dp 圆角
 * - 首项：顶部 16.dp 圆角，底部 6.dp 圆角
 * - 中间项：四角 6.dp 圆角
 * - 末项：顶部 6.dp 圆角，底部 16.dp 圆角
 *
 * 使用示例：
 * ```
 * SplicedColumnGroup(title = "优化操作") {
 *     item { DriverSettingCard(...) }
 *     item(visible = hasPermission) { AdvancedCard() }
 * }
 * ```
 *
 * @param modifier 外层修饰符
 * @param title 分组标题（可选）
 * @param content DSL 构建器，用于添加项目
 */
@Composable
fun SplicedColumnGroup(
    modifier: Modifier = Modifier,
    title: String = "",
    content: SplicedGroupScope.() -> Unit
) {
    val scope = SplicedGroupScope().apply(content)
    val visibleItems = scope.items.filter { it.visible }

    Column(
        modifier = modifier
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            visibleItems.forEachIndexed { index, itemData ->
                // 根据可见项位置动态选择圆角形状
                val shape = when {
                    visibleItems.size == 1 -> AppShape.SplicedGroup.single
                    index == 0 -> AppShape.SplicedGroup.top
                    index == visibleItems.size - 1 -> AppShape.SplicedGroup.bottom
                    else -> AppShape.SplicedGroup.middle
                }

                Column(
                    modifier = Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceBright)
                ) {
                    itemData.content()
                }
            }
        }
    }
}

/**
 * SplicedColumnGroup 的 DSL 作用域
 *
 * 提供 `item()` 函数用于添加分组项目
 */
class SplicedGroupScope {
    internal val items = mutableListOf<SplicedItemData>()

    /**
     * 添加一个项目到分组
     *
     * @param visible 是否显示该项目，默认 true。可用于条件显示：
     *                ```
     *                item(visible = isRootUser) { RootFeatureCard() }
     *                ```
     * @param content 项目内容的 Composable 函数
     */
    fun item(visible: Boolean = true, content: @Composable () -> Unit) {
        items.add(SplicedItemData(visible, content))
    }
}

/**
 * 分组项目数据
 *
 * @property visible 是否可见
 * @property content 项目内容
 */
data class SplicedItemData(
    val visible: Boolean,
    val content: @Composable () -> Unit
)
