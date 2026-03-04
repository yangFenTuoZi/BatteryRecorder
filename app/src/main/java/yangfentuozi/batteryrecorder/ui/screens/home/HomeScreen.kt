package yangfentuozi.batteryrecorder.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.server.recorder.IRecordListener
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.home.BatteryRecorderTopAppBar
import yangfentuozi.batteryrecorder.ui.components.home.CurrentRecordCard
import yangfentuozi.batteryrecorder.ui.components.home.PredictionCard
import yangfentuozi.batteryrecorder.ui.components.home.SceneStatsCard
import yangfentuozi.batteryrecorder.ui.components.home.StartServerCard
import yangfentuozi.batteryrecorder.ui.components.home.StatsCard
import yangfentuozi.batteryrecorder.ui.dialog.home.AboutDialog
import yangfentuozi.batteryrecorder.ui.dialog.home.AdbGuideDialog
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.ui.viewmodel.LiveRecordViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    liveRecordViewModel: LiveRecordViewModel = viewModel(),
    settingsViewModel: SettingsViewModel,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistoryList: (BatteryStatus) -> Unit = {},
    onNavigateToRecordDetail: (BatteryStatus, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val serviceConnected by viewModel.serviceConnected.collectAsState()
    val showStopDialog by viewModel.showStopDialog.collectAsState()
    val showAboutDialog by viewModel.showAboutDialog.collectAsState()
    var showAdbGuideDialog by remember { mutableStateOf(false) }
    val chargeSummary by viewModel.chargeSummary.collectAsState()
    val dischargeSummary by viewModel.dischargeSummary.collectAsState()
    val currentRecord by viewModel.currentRecord.collectAsState()
    val liveStatus by liveRecordViewModel.lastStatus.collectAsState()

    val settingsState by settingsViewModel.settingsUiState.collectAsState()
    val statisticsRequest by settingsViewModel.statisticsRequest.collectAsState()
    val latestStatisticsRequest by rememberUpdatedState(statisticsRequest)
    var prevServiceConnected by remember { mutableStateOf(serviceConnected) }
    val dualCellEnabled = settingsState.dualCellEnabled
    val calibrationValue = settingsState.calibrationValue
    val intervalMs = settingsState.recordIntervalMs
    val dischargeDisplayPositive = settingsState.dischargeDisplayPositive

    // 场景统计和预测
    val sceneStats by viewModel.sceneStats.collectAsState()
    val prediction by viewModel.prediction.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    val listener = remember { object : IRecordListener.Stub() {
            override fun onRecord(timestamp: Long, power: Long, status: BatteryStatus, temp: Int) {
                liveRecordViewModel.handleRecord(power, status, temp)
            }

            override fun onChangedCurrRecordsFile() {
                viewModel.forceRefreshStatistics(
                    context = context,
                    request = latestStatisticsRequest
                )
            }
        }
    }

    LaunchedEffect(serviceConnected) {
        val shouldDoDelayedRefresh = serviceConnected && !prevServiceConnected
        prevServiceConnected = serviceConnected
        if (!shouldDoDelayedRefresh) return@LaunchedEffect

        Service.service?.registerRecordListener(listener)
        run {
            delay(1500)
            viewModel.refreshStatistics(
                context = context,
                request = statisticsRequest
            )
        }
    }

    LaunchedEffect(liveStatus) {
        viewModel.onLiveStatusChanged(context, liveStatus, intervalMs)
    }

    // 监听生命周期事件
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.refreshStatistics(
                        context = context,
                        request = latestStatisticsRequest
                    )
                    Service.service?.registerRecordListener(listener)
                }

                Lifecycle.Event.ON_STOP -> {
                    Service.service?.unregisterRecordListener(listener)
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
                    onRefreshClick = {
                        viewModel.forceRefreshStatistics(
                            context = context,
                            request = statisticsRequest
                        )
                    },
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
                    // Root 启动卡片
                    item(visible = !serviceConnected) {
                        StartServerCard()
                    }

                    // ADB 启动卡片
                    item(visible = !serviceConnected) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "启动（ADB）",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "通过 ADB 命令启动",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Button(
                                shape = AppShape.SplicedGroup.single,
                                onClick = { showAdbGuideDialog = true }
                            ) {
                                Text("查看命令")
                            }
                        }
                    }

                    item {
                        CurrentRecordCard(
                            record = currentRecord,
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = calibrationValue,
                            viewModel = liveRecordViewModel,
                            dischargeDisplayPositive = dischargeDisplayPositive,
                            onClick = {
                                currentRecord?.let { record ->
                                    onNavigateToRecordDetail(record.type, record.name)
                                }
                            }
                        )
                    }

                    val isDischarging = currentRecord?.type != BatteryStatus.Charging

                    if (isDischarging) {
                        item {
                            PredictionCard(prediction = prediction)
                        }
                    }

                    // 统计卡片行（自动处理圆角）
                    rowItem {
                        item {
                            StatsCard(
                                title = "充电总结",
                                summary = chargeSummary,
                                dualCellEnabled = dualCellEnabled,
                                calibrationValue = calibrationValue,
                                onClick = { onNavigateToHistoryList(BatteryStatus.Charging) }
                            )
                        }
                        item {
                            StatsCard(
                                title = "放电总结",
                                summary = dischargeSummary,
                                dualCellEnabled = dualCellEnabled,
                                calibrationValue = calibrationValue,
                                onClick = { onNavigateToHistoryList(BatteryStatus.Discharging) }
                            )
                        }
                    }

                    item {
                        SceneStatsCard(
                            sceneStats = sceneStats,
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = calibrationValue,
                            dischargeDisplayPositive = dischargeDisplayPositive
                        )
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

    // ADB Guide Dialog
    if (showAdbGuideDialog) {
        AdbGuideDialog(
            onDismiss = { showAdbGuideDialog = false }
        )
    }
}
