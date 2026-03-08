package yangfentuozi.batteryrecorder.ui.components.global

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HardLineBreak
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.Parser

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val document = remember(markdown) {
        Parser.builder().build().parse(markdown)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarkdownBlocks(node = document)
    }
}

@Composable
private fun MarkdownBlocks(node: Node) {
    var child = node.firstChild
    while (child != null) {
        MarkdownBlock(node = child)
        child = child.next
    }
}

@Composable
private fun MarkdownBlock(node: Node) {
    when (node) {
        is Heading -> MarkdownInlineText(
            node = node,
            style = when (node.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
        )

        is Paragraph -> MarkdownInlineText(
            node = node,
            style = MaterialTheme.typography.bodyMedium
        )

        is BulletList -> MarkdownList(node = node)
        is OrderedList -> MarkdownList(node = node)

        is FencedCodeBlock -> MarkdownCodeBlock(code = node.literal)
        is IndentedCodeBlock -> MarkdownCodeBlock(code = node.literal)

        is BlockQuote -> Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "|",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkdownBlocks(node = node)
            }
        }
    }
}

@Composable
private fun MarkdownList(node: Node) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var child = node.firstChild
        var index = 1
        while (child != null) {
            if (child is ListItem) {
                MarkdownListItem(
                    marker = if (node is OrderedList) "$index." else "-",
                    item = child
                )
                index += 1
            }
            child = child.next
        }
    }
}

@Composable
private fun MarkdownListItem(
    marker: String,
    item: ListItem
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MarkdownBlocks(node = item)
        }
    }
}

@Composable
private fun MarkdownCodeBlock(code: String) {
    Text(
        text = code.trimEnd(),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp)
    )
}

@Composable
private fun MarkdownInlineText(
    node: Node,
    style: TextStyle
) {
    val uriHandler = LocalUriHandler.current
    val linkStyle = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val annotatedString = remember(node, style, linkStyle) {
        buildAnnotatedString {
            appendInlineNodes(node = node, linkColor = linkStyle)
        }
    }
    ClickableText(
        text = annotatedString,
        style = style.copy(color = textColor),
        onClick = { offset ->
            annotatedString
                .getStringAnnotations(tag = "url", start = offset, end = offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        }
    )
}

private fun AnnotatedString.Builder.appendInlineNodes(
    node: Node,
    linkColor: androidx.compose.ui.graphics.Color
) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is org.commonmark.node.Text -> append(child.literal)
            is SoftLineBreak -> append('\n')
            is HardLineBreak -> append('\n')
            is Emphasis -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInlineNodes(child, linkColor)
                pop()
            }

            is StrongEmphasis -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlineNodes(child, linkColor)
                pop()
            }

            is Code -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = linkColor.copy(alpha = 0.12f)
                    )
                )
                append(child.literal)
                pop()
            }

            is Link -> {
                pushStringAnnotation(tag = "url", annotation = child.destination)
                pushStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                )
                appendInlineNodes(child, linkColor)
                pop()
                pop()
            }

            else -> appendInlineNodes(child, linkColor)
        }
        child = child.next
    }
}
