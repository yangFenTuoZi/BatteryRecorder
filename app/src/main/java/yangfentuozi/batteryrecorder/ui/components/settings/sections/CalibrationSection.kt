package yangfentuozi.batteryrecorder.ui.components.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.ui.components.global.M3ESwitchWidget
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.dialog.settings.CalibrationDialog

@Composable
fun CalibrationSection(
    dualCellEnabled: Boolean,
    onDualCellChange: (Boolean) -> Unit,
    dischargeDisplayPositive: Boolean,
    onDischargeDisplayPositiveChange: (Boolean) -> Unit,
    calibrationValue: Int,
    onCalibrationChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var rawCurrent by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(showDialog) {
        if (showDialog) {
            while (true) {
                withContext(Dispatchers.IO) {
                    rawCurrent = try {
                        Service.service?.getCurrent()
                    } catch (_: Exception) {
                        null
                    }
                }
                delay(1000)
            }
        }
    }

    SplicedColumnGroup(
        title = "校准",
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            M3ESwitchWidget(
                text = "串联双电芯",
                checked = dualCellEnabled,
                onCheckedChange = onDualCellChange
            )
        }

        item {
            M3ESwitchWidget(
                text = "放电也显示正值",
                checked = dischargeDisplayPositive,
                onCheckedChange = onDischargeDisplayPositiveChange
            )
        }

        item {
            SettingsItem(
                title = "电流单位校准",
                summary = "调整电流读数的倍率"
            ) { showDialog = true }
        }
    }

    if (showDialog) {
        CalibrationDialog(
            currentValue = calibrationValue,
            rawCurrentMicroAmp = rawCurrent,
            serviceConnected = Service.service != null,
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

