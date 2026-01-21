package yangfentuozi.batteryrecorder.ui.compose.components.global

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle

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
            visibleItems.forEachIndexed { vIndex, itemData ->
                val isVerticalFirst = vIndex == 0
                val isVerticalLast = vIndex == visibleItems.size - 1

                key(itemData.key ?: vIndex) {
                    when (itemData.type) {
                        SplicedItemType.NORMAL -> {
                            // 普通单列 item
                            val shape = splicedCornerRadius(
                                isVerticalFirst = isVerticalFirst,
                                isVerticalLast = isVerticalLast
                            )

                            Column(
                                modifier = Modifier
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surfaceBright)
                            ) {
                                itemData.content()
                            }
                        }

                        SplicedItemType.ROW -> {
                            // 水平布局 item
                            val visibleRowItems = itemData.rowItems.filter { it.visible }

                            Row(
                                modifier = Modifier.height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                visibleRowItems.forEachIndexed { hIndex, rowItem ->
                                    val isHorizontalFirst = hIndex == 0
                                    val isHorizontalLast = hIndex == visibleRowItems.size - 1

                                    val shape = splicedCornerRadius(
                                        isVerticalFirst = isVerticalFirst,
                                        isVerticalLast = isVerticalLast,
                                        isHorizontalFirst = isHorizontalFirst,
                                        isHorizontalLast = isHorizontalLast
                                    )

                                    key(rowItem.key ?: hIndex) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(shape)
                                                .background(MaterialTheme.colorScheme.surfaceBright)
                                        ) {
                                            rowItem.content()
                                        }
                                    }
                                }
                            }
                        }
                    }
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
    fun item(
        key: Any? = null,
        visible: Boolean = true,
        content: @Composable () -> Unit
    ) {
        items.add(SplicedItemData(
            key = key,
            visible = visible,
            type = SplicedItemType.NORMAL,
            content = content
        ))
    }

    /**
     * 添加一个水平布局项到分组
     *
     * 用于创建并排显示的多个子项，自动处理圆角拼接
     *
     * @param visible 是否显示该行，默认 true
     * @param content DSL 构建器，用于添加行内的子项
     */
    fun rowItem(
        key: Any? = null,
        visible: Boolean = true,
        content: SplicedGroupScope.() -> Unit
    ) {
        val rowScope = SplicedGroupScope().apply(content)
        items.add(SplicedItemData(
            key = key,
            visible = visible,
            type = SplicedItemType.ROW,
            rowItems = rowScope.items
        ))
    }
}

/**
 * 分组项目数据
 *
 * @property visible 是否可见
 * @property type 项目类型（普通单列或水平布局）
 * @property content 项目内容
 * @property rowItems 行内子项（仅当 type 为 ROW 时有效）
 */
data class SplicedItemData(
    val key: Any? = null,
    val visible: Boolean,
    val type: SplicedItemType = SplicedItemType.NORMAL,
    val content: @Composable () -> Unit = {},
    val rowItems: List<SplicedItemData> = emptyList()
)

/**
 * 拼接项目类型
 */
enum class SplicedItemType {
    NORMAL,  // 普通单列 item
    ROW      // 水平布局 item（包含多个子项）
}

/**
 * 计算拼接项的圆角
 *
 * @param isVerticalFirst 是否是垂直方向的第一项
 * @param isVerticalLast 是否是垂直方向的最后一项
 * @param isHorizontalFirst 是否是水平方向的第一项（仅对 ROW 类型有效）
 * @param isHorizontalLast 是否是水平方向的最后一项（仅对 ROW 类型有效）
 */
private fun getCornerRadius(
    isVerticalFirst: Boolean,
    isVerticalLast: Boolean,
    isHorizontalFirst: Boolean = true,
    isHorizontalLast: Boolean = true
): ContinuousRoundedRectangle {
    return splicedCornerRadius(
        isVerticalFirst = isVerticalFirst,
        isVerticalLast = isVerticalLast,
        isHorizontalFirst = isHorizontalFirst,
        isHorizontalLast = isHorizontalLast
    )
}
