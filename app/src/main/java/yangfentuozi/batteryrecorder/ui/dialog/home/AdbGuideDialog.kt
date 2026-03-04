package yangfentuozi.batteryrecorder.ui.dialog.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private data class CommandItem(
    val title: String,
    val command: String
)

@Composable
fun AdbGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shellCommand = remember {
        "CLASSPATH=$(pm path yangfentuozi.batteryrecorder | cut -d: -f2) app_process /system/bin yangfentuozi.batteryrecorder.server.Main"
    }
    val shellNoHupCommand = remember(shellCommand) {
        "CLASSPATH=$(pm path yangfentuozi.batteryrecorder | cut -d: -f2) nohup app_process /system/bin yangfentuozi.batteryrecorder.server.Main >/dev/null 2>&1 &"
    }
    val commandItems = remember(shellCommand, shellNoHupCommand) {
        listOf(
            CommandItem(
                title = "ADB 场景：电脑端已连接设备，直接执行",
                command = "adb shell \"$shellCommand\""
            ),
            CommandItem(
                title = "纯 Shell 场景：已进入设备 shell 环境",
                command = shellCommand
            ),
            CommandItem(
                title = "纯 Shell 场景：后台常驻启动（推荐）",
                command = shellNoHupCommand
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADB 启动") },
        text = {
            Column {
                Text(
                    text = "1. 确保设备已开启 USB 调试或无线调试",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "2. 在终端中执行以下命令",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                commandItems.forEachIndexed { index, item ->
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    CommandBox(
                        command = item.command,
                        onCopy = { copyCommand(context, item.command) }
                    )
                    if (index != commandItems.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun CommandBox(
    command: String,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .height(MaterialTheme.typography.bodySmall.lineHeight.value.dp)
                    .width(MaterialTheme.typography.bodySmall.lineHeight.value.dp + 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制命令"
                )
            }
        }
    }
}

private fun copyCommand(context: Context, command: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("command", command))
    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}

@Preview
@Composable
fun AdbGuideDialogPreview() {
    AdbGuideDialog(onDismiss = {})
}
