package yangfentuozi.batteryrecorder.ui.compose.srceens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import yangfentuozi.batteryrecorder.ui.compose.components.AboutDialog
import yangfentuozi.batteryrecorder.ui.compose.components.BatteryRecorderTopAppBar
import yangfentuozi.batteryrecorder.ui.compose.components.global.SplicedRowGroup
import yangfentuozi.batteryrecorder.ui.compose.srceens.home.items.ChargeStatsCard
import yangfentuozi.batteryrecorder.ui.compose.srceens.home.items.DischargeStatsCard
import yangfentuozi.batteryrecorder.ui.compose.srceens.home.items.StartServerCard
import yangfentuozi.batteryrecorder.ui.compose.theme.AppShape
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val serviceConnected by viewModel.serviceConnected.collectAsState()
    val showStopDialog by viewModel.showStopDialog.collectAsState()
    val showAboutDialog by viewModel.showAboutDialog.collectAsState()
    val chargeStats by viewModel.chargeStats.collectAsState()
    val dischargeStats by viewModel.dischargeStats.collectAsState()

    // 读取设置值
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    // 初始化设置 ViewModel
    LaunchedEffect(Unit) {
        settingsViewModel.init(context)
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
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 启动卡片（条件显示）
                if (!serviceConnected) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(AppShape.SplicedGroup.homeStartCard)
                            .background(MaterialTheme.colorScheme.surfaceBright)
                    ) {
                        StartServerCard()
                    }
                }

                // 统计卡片行
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 充电统计
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(
                                if (serviceConnected) {
                                    // 服务已连接，统计卡片是顶部项
                                    ContinuousRoundedRectangle(
                                        topStart = 16.dp,
                                        topEnd = 6.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 6.dp
                                    )
                                } else {
                                    // 服务未连接，统计卡片是底部项
                                    AppShape.SplicedRow.homeChargeStats
                                }
                            )
                            .background(MaterialTheme.colorScheme.surfaceBright)
                    ) {
                        ChargeStatsCard(
                            stats = chargeStats,
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = calibrationValue
                        )
                    }

                    // 放电统计
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(
                                if (serviceConnected) {
                                    // 服务已连接，统计卡片是顶部项
                                    ContinuousRoundedRectangle(
                                        topStart = 6.dp,
                                        topEnd = 16.dp,
                                        bottomStart = 6.dp,
                                        bottomEnd = 16.dp
                                    )
                                } else {
                                    // 服务未连接，统计卡片是底部项
                                    AppShape.SplicedRow.homeDischargeStats
                                }
                            )
                            .background(MaterialTheme.colorScheme.surfaceBright)
                    ) {
                        DischargeStatsCard(
                            stats = dischargeStats,
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = calibrationValue
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
}
