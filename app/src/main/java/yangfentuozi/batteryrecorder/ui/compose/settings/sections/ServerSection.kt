package yangfentuozi.batteryrecorder.ui.compose.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import yangfentuozi.batteryrecorder.ui.compose.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.compose.settings.SettingsItemContainer
import yangfentuozi.batteryrecorder.ui.compose.settings.SettingsTitle
import yangfentuozi.batteryrecorder.ui.compose.settings.dialogs.BatchSizeDialog
import yangfentuozi.batteryrecorder.ui.compose.settings.dialogs.IntervalDialog
import yangfentuozi.batteryrecorder.ui.compose.settings.dialogs.WriteLatencyDialog

@Composable
fun ServerSection(
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit,
    writeLatencyMs: Long,
    onWriteLatencyChange: (Long) -> Unit,
    batchSize: Int,
    onBatchSizeChange: (Int) -> Unit
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showWriteLatencyDialog by remember { mutableStateOf(false) }
    var showBatchSizeDialog by remember { mutableStateOf(false) }

    Column {
        SettingsTitle("服务端")

        SettingsItemContainer {
            // 采样间隔
            SettingsItem(
                title = "采样间隔",
                summary = "${intervalMs / 1000.0} 秒",
            ) { showIntervalDialog = true }

            // 写入延迟
            SettingsItem(
                title = "写入延迟",
                summary = "${writeLatencyMs / 1000.0} 秒",
            ) { showWriteLatencyDialog = true }

            // 批量大小
            SettingsItem(
                title = "批量大小",
                summary = "$batchSize 条",
            ) { showBatchSizeDialog = true }
        }

        // 采样间隔对话框
        if (showIntervalDialog) {
            IntervalDialog(
                currentValueMs = intervalMs,
                onDismiss = { showIntervalDialog = false },
                onSave = { value ->
                    onIntervalChange(value)
                    showIntervalDialog = false
                },
                onReset = {
                    onIntervalChange(900)
                    showIntervalDialog = false
                }
            )
        }

        // 写入延迟对话框
        if (showWriteLatencyDialog) {
            WriteLatencyDialog(
                currentValueMs = writeLatencyMs,
                onDismiss = { showWriteLatencyDialog = false },
                onSave = { value ->
                    onWriteLatencyChange(value)
                    showWriteLatencyDialog = false
                },
                onReset = {
                    onWriteLatencyChange(5000)
                    showWriteLatencyDialog = false
                }
            )
        }

        // 批量大小对话框
        if (showBatchSizeDialog) {
            BatchSizeDialog(
                currentValue = batchSize,
                onDismiss = { showBatchSizeDialog = false },
                onSave = { value ->
                    onBatchSizeChange(value)
                    showBatchSizeDialog = false
                },
                onReset = {
                    onBatchSizeChange(200)
                    showBatchSizeDialog = false
                }
            )
        }
    }
}

