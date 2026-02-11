package yangfentuozi.batteryrecorder.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.data.history.RecordType
import yangfentuozi.batteryrecorder.data.history.SyncUtil
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.home.BatteryRecorderTopAppBar
import yangfentuozi.batteryrecorder.ui.components.home.ChargeStatsCard
import yangfentuozi.batteryrecorder.ui.components.home.CurrentRecordCard
import yangfentuozi.batteryrecorder.ui.components.home.DischargeStatsCard
import yangfentuozi.batteryrecorder.ui.components.home.StartServerCard
import yangfentuozi.batteryrecorder.ui.dialog.home.AboutDialog
import yangfentuozi.batteryrecorder.ui.viewmodel.LiveRecordViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    liveRecordViewModel: LiveRecordViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistoryList: (RecordType) -> Unit = {},
    onNavigateToRecordDetail: (RecordType, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val serviceConnected by viewModel.serviceConnected.collectAsState()
    val showStopDialog by viewModel.showStopDialog.collectAsState()
    val showAboutDialog by viewModel.showAboutDialog.collectAsState()
    val chargeSummary by viewModel.chargeSummary.collectAsState()
    val dischargeSummary by viewModel.dischargeSummary.collectAsState()
    val currentRecord by viewModel.currentRecord.collectAsState()
    val livePoints by liveRecordViewModel.livePoints.collectAsState()
    val liveStatus = livePoints.lastOrNull()?.status

    // 读取设置值
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val intervalMs by settingsViewModel.recordIntervalMs.collectAsState()
    val dischargeDisplayPositive by settingsViewModel.dischargeDisplayPositive.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    // 初始化设置 ViewModel
    LaunchedEffect(Unit) {
        settingsViewModel.init(context)
    }

    LaunchedEffect(intervalMs) {
        liveRecordViewModel.updateIntervalMs(intervalMs)
    }

    LaunchedEffect(liveStatus) {
        if (liveStatus == null) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            runCatching { SyncUtil.sync(context) }
        }

        val before = viewModel.currentRecord.value
        delay((intervalMs * 2).coerceAtLeast(800L))

        repeat(3) {
            viewModel.refreshCurrentRecord(context)
            delay(350L)
            val after = viewModel.currentRecord.value
            if (after?.name != before?.name || after?.type != before?.type) return@LaunchedEffect
        }
    }

    // 监听生命周期事件
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.refreshStatistics(context)
                }

                Lifecycle.Event.ON_STOP -> {
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface {
        Scaffold(
            topBar = {
                BatteryRecorderTopAppBar(
                    onSettingsClick = onNavigateToSettings,
                    onStopServerClick = viewModel::showStopDialog,
                    onAboutClick = viewModel::showAboutDialog,
                    showStopServer = serviceConnected
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(vertical = 8.dp)
            ) {
                SplicedColumnGroup(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    // 启动卡片（条件显示）
                    item(visible = !serviceConnected) {
                        StartServerCard()
                    }

                    item {
                        CurrentRecordCard(
                            record = currentRecord,
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = calibrationValue,
                            livePoints = livePoints,
                            dischargeDisplayPositive = dischargeDisplayPositive,
                            onClick = {
                                currentRecord?.let { record ->
                                    onNavigateToRecordDetail(record.type, record.name)
                                }
                            }
                        )
                    }

                    // 统计卡片行（自动处理圆角）
                    rowItem {
                        item {
                            ChargeStatsCard(
                                summary = chargeSummary,
                                dualCellEnabled = dualCellEnabled,
                                calibrationValue = calibrationValue,
                                onClick = { onNavigateToHistoryList(RecordType.CHARGE) }
                            )
                        }
                        item {
                            DischargeStatsCard(
                                summary = dischargeSummary,
                                dualCellEnabled = dualCellEnabled,
                                calibrationValue = calibrationValue,
                                onClick = { onNavigateToHistoryList(RecordType.DISCHARGE) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Stop Server Dialog
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissStopDialog,
            title = { Text("停止服务") },
            text = { Text("确认停止服务?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissStopDialog()
                        viewModel.stopService()
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissStopDialog
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = viewModel::dismissAboutDialog
        )
    }
}
