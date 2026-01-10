package yangfentuozi.batteryrecorder.ui.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.Service
import yangfentuozi.batteryrecorder.ui.compose.settings.dialogs.BatchSizeDialog
import yangfentuozi.batteryrecorder.ui.compose.settings.dialogs.IntervalDialog

@Composable
fun ServerSection(
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit,
    batchSize: Int,
    onBatchSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showBatchSizeDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "服务器",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 采样间隔
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { showIntervalDialog = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "采样间隔",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${intervalMs / 1000.0} 秒",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 批量大小
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { showBatchSizeDialog = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "批量大小",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "$batchSize 条",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 采样间隔对话框
    if (showIntervalDialog) {
        IntervalDialog(
            currentValueMs = intervalMs,
            onDismiss = { showIntervalDialog = false },
            onSave = { value ->
                onIntervalChange(value)
                Service.service?.refreshConfig()
                showIntervalDialog = false
            },
            onReset = {
                onIntervalChange(900)
                Service.service?.refreshConfig()
                showIntervalDialog = false
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
                Service.service?.refreshConfig()
                showBatchSizeDialog = false
            },
            onReset = {
                onBatchSizeChange(20)
                Service.service?.refreshConfig()
                showBatchSizeDialog = false
            }
        )
    }
}

