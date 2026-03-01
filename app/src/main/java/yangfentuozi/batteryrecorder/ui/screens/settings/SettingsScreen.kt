package yangfentuozi.batteryrecorder.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.ui.components.settings.sections.CalibrationSection
import yangfentuozi.batteryrecorder.ui.components.settings.sections.GameListSection
import yangfentuozi.batteryrecorder.ui.components.settings.sections.PredictionSection
import yangfentuozi.batteryrecorder.ui.components.settings.sections.ServerSection
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    // 读取设置值
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val dischargeDisplayPositive by settingsViewModel.dischargeDisplayPositive.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val intervalMs by settingsViewModel.recordIntervalMs.collectAsState()
    val writeLatencyMs by settingsViewModel.writeLatencyMs.collectAsState()
    val batchSize by settingsViewModel.batchSize.collectAsState()
    val recordScreenOffEnabled by settingsViewModel.screenOffRecord.collectAsState()
    val segmentDurationMin by settingsViewModel.segmentDurationMin.collectAsState()
    val gamePackages by settingsViewModel.gamePackages.collectAsState()
    val gameBlacklist by settingsViewModel.gameBlacklist.collectAsState()
    val predCurrentSessionWeightEnabled by settingsViewModel.predCurrentSessionWeightEnabled.collectAsState()
    val predCurrentSessionWeightMaxX100 by settingsViewModel.predCurrentSessionWeightMaxX100.collectAsState()
    val predCurrentSessionWeightHalfLifeMin by settingsViewModel.predCurrentSessionWeightHalfLifeMin.collectAsState()
    val serviceConnected = Service.service != null

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("设置") },
                scrollBehavior = scrollBehavior,
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp)
        ) {
            item {
                // 校准设置
                CalibrationSection(
                    dualCellEnabled = dualCellEnabled,
                    onDualCellChange = settingsViewModel::setDualCellEnabled,
                    dischargeDisplayPositive = dischargeDisplayPositive,
                    onDischargeDisplayPositiveChange = settingsViewModel::setDischargeDisplayPositiveEnabled,
                    calibrationValue = calibrationValue,
                    serviceConnected = serviceConnected,
                    onCalibrationChange = settingsViewModel::setCalibrationValue
                )
            }
            item { Spacer(modifier = Modifier.size(16.dp)) }
            item {
                // 服务器设置
                ServerSection(
                    recordIntervalMs = intervalMs,
                    onRecordIntervalChange = settingsViewModel::setRecordIntervalMs,
                    writeLatencyMs = writeLatencyMs,
                    onWriteLatencyChange = settingsViewModel::setWriteLatencyMs,
                    batchSize = batchSize,
                    onBatchSizeChange = settingsViewModel::setBatchSize,
                    recordScreenOffEnabled = recordScreenOffEnabled,
                    onRecordScreenOffChange = settingsViewModel::setScreenOffRecordEnabled,
                    segmentDurationMin = segmentDurationMin,
                    onSegmentDurationChange = settingsViewModel::setSegmentDurationMin,
                )
            }
            item { Spacer(modifier = Modifier.size(16.dp)) }
            item {
                GameListSection(
                    gamePackages = gamePackages,
                    gameBlacklist = gameBlacklist,
                    onGamePackagesChange = settingsViewModel::setGamePackages
                )
            }
            item { Spacer(modifier = Modifier.size(16.dp)) }
            item {
                PredictionSection(
                    currentSessionWeightEnabled = predCurrentSessionWeightEnabled,
                    onCurrentSessionWeightEnabledChange = settingsViewModel::setPredCurrentSessionWeightEnabled,
                    currentSessionWeightMaxX100 = predCurrentSessionWeightMaxX100,
                    onCurrentSessionWeightMaxX100Change = settingsViewModel::setPredCurrentSessionWeightMaxX100,
                    currentSessionWeightHalfLifeMin = predCurrentSessionWeightHalfLifeMin,
                    onCurrentSessionWeightHalfLifeMinChange = settingsViewModel::setPredCurrentSessionWeightHalfLifeMin
                )
            }
        }
    }
}
