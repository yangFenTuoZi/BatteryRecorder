package yangfentuozi.batteryrecorder.ui.compose.srceens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.sections.CalibrationSection
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.sections.ServerSection
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // 初始化 ViewModel
    LaunchedEffect(Unit) {
        settingsViewModel.init(context)
    }

    // 读取设置值
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val intervalMs by settingsViewModel.intervalMs.collectAsState()
    val writeLatencyMs by settingsViewModel.writeLatencyMs.collectAsState()
    val batchSize by settingsViewModel.batchSize.collectAsState()
    val recordScreenOffEnabled by settingsViewModel.recordScreenOff.collectAsState()

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
                    calibrationValue = calibrationValue,
                    onCalibrationChange = settingsViewModel::setCalibrationValue
                )
            }
            item { Spacer(modifier = Modifier.size(16.dp)) }
            item {
                // 服务器设置
                ServerSection(
                    intervalMs = intervalMs,
                    onIntervalChange = settingsViewModel::setIntervalMs,
                    writeLatencyMs = writeLatencyMs,
                    onWriteLatencyChange = settingsViewModel::setWriteLatencyMs,
                    batchSize = batchSize,
                    onBatchSizeChange = settingsViewModel::setBatchSize,
                    recordScreenOffEnabled = recordScreenOffEnabled,
                    onRecordScreenOffChange = settingsViewModel::setRecordScreenOffEnabled,
                )
            }
        }
    }
}
