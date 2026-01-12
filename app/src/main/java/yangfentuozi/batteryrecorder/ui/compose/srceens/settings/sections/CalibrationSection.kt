package yangfentuozi.batteryrecorder.ui.compose.srceens.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.SettingsSwitchItem
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.SettingsTitle
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.dialogs.CalibrationDialog

@Composable
fun CalibrationSection(
    dualCellEnabled: Boolean,
    onDualCellChange: (Boolean) -> Unit,
    calibrationValue: Int,
    onCalibrationChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column {
        SettingsTitle("校准")

        // 双电池开关
        SettingsSwitchItem(
            text = "双电池",
            checked = dualCellEnabled,
            onCheckedChange = onDualCellChange
        )

        // 电流单位校准
        SettingsItem(
            title = "电流单位校准",
            summary = "调整电流读数的倍率"
        ) { showDialog = true }

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
}

