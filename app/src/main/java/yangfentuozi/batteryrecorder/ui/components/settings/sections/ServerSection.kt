package yangfentuozi.batteryrecorder.ui.components.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.components.global.M3ESwitchWidget
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.dialog.settings.BatchSizeDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.IntervalDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.SegmentDurationDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.WriteLatencyDialog
import kotlin.math.round

@Composable
fun ServerSection(
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit,
    writeLatencyMs: Long,
    onWriteLatencyChange: (Long) -> Unit,
    batchSize: Int,
    onBatchSizeChange: (Int) -> Unit,
    recordScreenOffEnabled: Boolean,
    onRecordScreenOffChange: (Boolean) -> Unit,
    segmentDurationMin: Long,
    onSegmentDurationChange: (Long) -> Unit
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showWriteLatencyDialog by remember { mutableStateOf(false) }
    var showBatchSizeDialog by remember { mutableStateOf(false) }
    var showSegmentDurationDialog by remember { mutableStateOf(false) }

    SplicedColumnGroup(
        title = "服务端",
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            M3ESwitchWidget(
                text = "息屏记录",
                checked = recordScreenOffEnabled,
                onCheckedChange = onRecordScreenOffChange
            )
        }

        item {
            SettingsItem(
                title = "采样间隔",
                summary = "${"%.1f".format(intervalMs / 1000.0)} 秒"
            ) { showIntervalDialog = true }
        }

        item {
            SettingsItem(
                title = "写入延迟",
                summary = "${"%.1f".format(writeLatencyMs / 1000.0)} 秒"
            ) { showWriteLatencyDialog = true }
        }

        item {
            SettingsItem(
                title = "批量大小",
                summary = "$batchSize 条"
            ) { showBatchSizeDialog = true }
        }

        item {
            val summary = if (segmentDurationMin == 0L) {
                "不按时间分段"
            } else {
                "$segmentDurationMin 分钟"
            }
            SettingsItem(
                title = "分段时间",
                summary = summary
            ) { showSegmentDurationDialog = true }
        }
    }

    // 采样间隔对话框
    if (showIntervalDialog) {
        IntervalDialog(
            currentValueMs = intervalMs,
            onDismiss = { showIntervalDialog = false },
            onSave = { value ->
                val roundedValue = (round(value / 100.0) * 100).toLong()
                onIntervalChange(roundedValue)
                showIntervalDialog = false
            },
            onReset = {
                onIntervalChange(1000)
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
                val roundedValue = (round(value / 100.0) * 100).toLong()
                onWriteLatencyChange(roundedValue)
                showWriteLatencyDialog = false
            },
            onReset = {
                onWriteLatencyChange(30000)
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

    // 分段时间对话框
    if (showSegmentDurationDialog) {
        SegmentDurationDialog(
            currentValueMin = segmentDurationMin,
            onDismiss = { showSegmentDurationDialog = false },
            onSave = { value ->
                onSegmentDurationChange(value)
                showSegmentDurationDialog = false
            },
            onReset = {
                onSegmentDurationChange(1440)
                showSegmentDurationDialog = false
            }
        )
    }
}
