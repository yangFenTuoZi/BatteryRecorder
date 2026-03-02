package yangfentuozi.batteryrecorder.ui.components.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.ui.components.global.M3ESwitchWidget
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.dialog.settings.CurrentSessionWeightDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.SceneStatsRecentFileCountDialog

@Composable
fun PredictionSection(
    sceneStatsRecentFileCount: Int,
    onSceneStatsRecentFileCountChange: (Int) -> Unit,
    currentSessionWeightEnabled: Boolean,
    onCurrentSessionWeightEnabledChange: (Boolean) -> Unit,
    currentSessionWeightMaxX100: Int,
    onCurrentSessionWeightMaxX100Change: (Int) -> Unit,
    currentSessionWeightHalfLifeMin: Long,
    onCurrentSessionWeightHalfLifeMinChange: (Long) -> Unit
) {
    var showWeightDialog by remember { mutableStateOf(false) }
    var showRecentCountDialog by remember { mutableStateOf(false) }

    SplicedColumnGroup(
        title = "预测",
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            SettingsItem(
                title = "样本次数",
                summary = "最近 ${sceneStatsRecentFileCount} 次"
            ) { showRecentCountDialog = true }
        }

        item {
            M3ESwitchWidget(
                text = "当次记录加权",
                checked = currentSessionWeightEnabled,
                onCheckedChange = onCurrentSessionWeightEnabledChange
            )
        }

        item {
            val maxX = currentSessionWeightMaxX100 / 100.0
            val summary = "最大 %.2fx / 半衰期 %d 分钟".format(maxX, currentSessionWeightHalfLifeMin)
            SettingsItem(
                title = "加权强度",
                summary = summary
            ) { showWeightDialog = true }
        }
    }

    if (showRecentCountDialog) {
        SceneStatsRecentFileCountDialog(
            currentValue = sceneStatsRecentFileCount,
            onDismiss = { showRecentCountDialog = false },
            onSave = { count ->
                onSceneStatsRecentFileCountChange(count)
                showRecentCountDialog = false
            },
            onReset = {
                onSceneStatsRecentFileCountChange(ConfigConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT)
                showRecentCountDialog = false
            }
        )
    }

    if (showWeightDialog) {
        CurrentSessionWeightDialog(
            currentMaxX100 = currentSessionWeightMaxX100,
            currentHalfLifeMin = currentSessionWeightHalfLifeMin,
            onDismiss = { showWeightDialog = false },
            onSave = { maxX100, halfLifeMin ->
                onCurrentSessionWeightMaxX100Change(maxX100)
                onCurrentSessionWeightHalfLifeMinChange(halfLifeMin)
                showWeightDialog = false
            },
            onReset = {
                onCurrentSessionWeightMaxX100Change(ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100)
                onCurrentSessionWeightHalfLifeMinChange(ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN)
                showWeightDialog = false
            }
        )
    }
}
