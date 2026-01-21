package yangfentuozi.batteryrecorder.ui.screens.settings.sections

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
import yangfentuozi.batteryrecorder.ui.screens.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.screens.settings.dialogs.CalibrationDialog

@Composable
fun CalibrationSection(
    dualCellEnabled: Boolean,
    onDualCellChange: (Boolean) -> Unit,
    calibrationValue: Int,
    onCalibrationChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    SplicedColumnGroup(
        title = "校准",
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            M3ESwitchWidget(
                text = "双电池",
                checked = dualCellEnabled,
                onCheckedChange = onDualCellChange
            )
        }

        item {
            SettingsItem(
                title = "电流单位校准",
                summary = "调整电流读数的倍率"
            ) { showDialog = true }
        }
    }

    // 显示对话框
    if (showDialog) {
        CalibrationDialog(
            currentValue = calibrationValue,
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

