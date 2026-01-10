package yangfentuozi.batteryrecorder.ui.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.compose.settings.dialogs.CalibrationDialog

@Composable
fun CalibrationSection(
    dualCellEnabled: Boolean,
    onDualCellChange: (Boolean) -> Unit,
    calibrationValue: Int,
    onCalibrationChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "校准",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 双电池开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "双电池",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = dualCellEnabled,
                onCheckedChange = onDualCellChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            )
        }

        // 电流单位校准
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { showDialog = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "电流单位校准",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "调整电流读数的倍率",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 显示对话框
        if (showDialog) {
            CalibrationDialog(
                currentValue = calibrationValue,
                onValueChange = onCalibrationChange,
                onDismiss = { showDialog = false },
                onSave = { value ->
                    onCalibrationChange(value)
                    showDialog = false
                },
                onReset = {
                    onCalibrationChange(-1)
                    showDialog = false
                }
            )
        }
    }
}

