package yangfentuozi.batteryrecorder.ui.components.global

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 基于 LazyColumn 的拼接列分组组件。
 *
 * 仅支持单列 item，视觉规则与 SplicedColumnGroup 保持一致。
 */
@Composable
fun <T> LazySplicedColumnGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    title: String = "",
    key: ((item: T) -> Any)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemContent: @Composable (item: T) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        if (title.isNotEmpty()) {
            item(key = "spliced_title") {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
        }

        itemsIndexed(
            items = items,
            key = if (key != null) {
                // 复用调用方提供的稳定 key，避免列表刷新时 item 状态错位。
                { _, item -> key(item) }
            } else {
                null
            }
        ) { index, item ->
            val shape = splicedCornerRadius(
                isVerticalFirst = index == 0,
                isVerticalLast = index == items.lastIndex
            )

            Column(
                modifier = Modifier
                    // 与 SplicedColumnGroup 保持一致，组内 item 之间用 2.dp 视觉缝隙分隔。
                    .padding(bottom = if (index == items.lastIndex) 0.dp else 2.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceBright)
            ) {
                itemContent(item)
            }
        }
    }
}
