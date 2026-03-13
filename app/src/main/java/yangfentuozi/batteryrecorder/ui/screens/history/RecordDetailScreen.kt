package yangfentuozi.batteryrecorder.ui.screens.history

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.ui.components.charts.FixedPowerAxisMode
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCapacityChart
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCurveMode
import yangfentuozi.batteryrecorder.ui.components.charts.RecordChartCurveVisibility
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.dialog.history.ChartGuideDialog
import yangfentuozi.batteryrecorder.ui.viewmodel.HistoryViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.formatDateTime
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatPower

private const val RECORD_DETAIL_CHART_PREFS_NAME = "record_detail_chart"
private const val KEY_POWER_CURVE_MODE = "power_curve_mode"
private const val KEY_SHOW_CAPACITY_CURVE = "show_capacity_curve"
private const val KEY_SHOW_TEMP_CURVE = "show_temp_curve"
private const val KEY_SHOW_APP_ICONS = "show_app_icons"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordsFile: RecordsFile,
    viewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val record by viewModel.recordDetail.collectAsState()
    val chartUiState by viewModel.recordChartUiState.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val dischargeDisplayPositive by settingsViewModel.dischargeDisplayPositive.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val recordScreenOffEnabled by settingsViewModel.screenOffRecord.collectAsState()
    val chartPrefs = remember(context) {
        context.getSharedPreferences(RECORD_DETAIL_CHART_PREFS_NAME, Context.MODE_PRIVATE)
    }
    // 这三项是“详情页图表本地展示偏好”，不属于业务配置，因此直接放在页面本地状态里持久化。
    var powerCurveMode by remember(chartPrefs) {
        mutableStateOf(loadPowerCurveMode(chartPrefs.getString(KEY_POWER_CURVE_MODE, null)))
    }
    var showCapacity by remember(chartPrefs) {
        mutableStateOf(chartPrefs.getBoolean(KEY_SHOW_CAPACITY_CURVE, true))
    }
    var showTemp by remember(chartPrefs) {
        mutableStateOf(chartPrefs.getBoolean(KEY_SHOW_TEMP_CURVE, true))
    }
    var showAppIcons by remember(chartPrefs) {
        mutableStateOf(chartPrefs.getBoolean(KEY_SHOW_APP_ICONS, true))
    }
    var isChartFullscreen by rememberSaveable(recordsFile) { mutableStateOf(false) }
    var fullscreenViewportStartMs by rememberSaveable(recordsFile) { mutableStateOf<Long?>(null) }
    var showDeleteDialog by rememberSaveable(recordsFile) { mutableStateOf(false) }
    var showGuideDialog by rememberSaveable(recordsFile) { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportRecord(context, recordsFile, uri)
        }
    }

    // 图表展示依赖设置页的功率换算配置与息屏过滤配置；
    // 这几个值任何一个变化，都需要让 ViewModel 重新生成 chartUiState。
    LaunchedEffect(dualCellEnabled, calibrationValue, recordScreenOffEnabled) {
        viewModel.updatePowerDisplayConfig(
            dualCellEnabled = dualCellEnabled,
            calibrationValue = calibrationValue,
            recordScreenOffEnabled = recordScreenOffEnabled
        )
    }

    LaunchedEffect(recordsFile) {
        // 详情页切换记录文件时，重新加载文件内容与图表点。
        viewModel.loadRecord(context, recordsFile)
    }
    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeUserMessage()
        if (message == "删除成功") {
            onNavigateBack()
        }
    }

    BackHandler(enabled = isChartFullscreen) {
        isChartFullscreen = false
        fullscreenViewportStartMs = null
    }

    LaunchedEffect(activity, isChartFullscreen) {
        if (activity != null) {
            activity.requestedOrientation = if (isChartFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isChartFullscreen) {
                TopAppBar(
                    title = { Text("记录详情") },
                    actions = {
                        IconButton(
                            onClick = { exportLauncher.launch(recordsFile.name) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Outbox,
                                contentDescription = "导出记录"
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "删除记录"
                            )
                        }
                        IconButton(
                            onClick = { showGuideDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "查看图表说明"
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        val detail = record
        if (detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val stats = detail.stats
        val durationMs = stats.endTime - stats.startTime
        val capacityChange = if (detail.type == BatteryStatus.Charging) {
            stats.endCapacity - stats.startCapacity
        } else {
            stats.startCapacity - stats.endCapacity
        }
        val typeLabel = if (detail.type == BatteryStatus.Charging) "充电记录" else "放电记录"

        val fixedPowerMode =
            if (detail.type == BatteryStatus.Discharging && !dischargeDisplayPositive) {
                FixedPowerAxisMode.NegativeOnly
            } else {
                FixedPowerAxisMode.PositiveOnly
            }
        val curveVisibility = RecordChartCurveVisibility(
            powerCurveMode = powerCurveMode,
            showCapacity = showCapacity,
            showTemp = showTemp
        )

        // 只有全屏模式允许横向拖动浏览局部视口；
        // 非全屏直接展示完整时长，减少普通详情页的认知负担。
        val viewportStartForChart = if (isChartFullscreen && chartUiState.minChartTime != null) {
            val minChartTime = chartUiState.minChartTime!!
            val maxViewportStart = chartUiState.maxViewportStartTime
            val initialStart = fullscreenViewportStartMs ?: minChartTime
            if (maxViewportStart == null) {
                initialStart
            } else {
                initialStart.coerceIn(minChartTime, maxViewportStart)
            }
        } else {
            null
        }
        val viewportEndForChart = if (
            isChartFullscreen &&
            viewportStartForChart != null &&
            chartUiState.maxChartTime != null
        ) {
            (viewportStartForChart + chartUiState.viewportDurationMs)
                .coerceAtMost(chartUiState.maxChartTime!!)
        } else {
            null
        }

        // chartBlock 统一封装普通模式 / 全屏模式下的同一张图表，避免两套 UI 结构分叉。
        val chartBlock: @Composable (Modifier, Boolean) -> Unit = { modifier, isFullscreenMode ->
            PowerCapacityChart(
                points = chartUiState.points,
                trendPoints = chartUiState.trendPoints,
                recordScreenOffEnabled = recordScreenOffEnabled,
                recordStartTime = stats.startTime,
                modifier = modifier,
                fixedPowerAxisMode = fixedPowerMode,
                curveVisibility = curveVisibility,
                chartHeight = if (isFullscreenMode) 320.dp else 240.dp,
                isFullscreen = isFullscreenMode,
                onToggleFullscreen = {
                    if (isChartFullscreen) {
                        isChartFullscreen = false
                        fullscreenViewportStartMs = null
                    } else {
                        isChartFullscreen = true
                        fullscreenViewportStartMs = chartUiState.minChartTime
                    }
                },
                onTogglePowerVisibility = {
                    val nextValue = powerCurveMode.next()
                    chartPrefs.edit { putString(KEY_POWER_CURVE_MODE, nextValue.name) }
                    powerCurveMode = nextValue
                },
                onToggleCapacityVisibility = {
                    val nextValue = !showCapacity
                    chartPrefs.edit { putBoolean(KEY_SHOW_CAPACITY_CURVE, nextValue) }
                    showCapacity = nextValue
                },
                onToggleTempVisibility = {
                    val nextValue = !showTemp
                    chartPrefs.edit { putBoolean(KEY_SHOW_TEMP_CURVE, nextValue) }
                    showTemp = nextValue
                },
                showAppIcons = showAppIcons,
                onToggleAppIconsVisibility = {
                    val nextValue = !showAppIcons
                    chartPrefs.edit { putBoolean(KEY_SHOW_APP_ICONS, nextValue) }
                    showAppIcons = nextValue
                },
                useFivePercentTimeGrid = isFullscreenMode,
                visibleStartTime = viewportStartForChart,
                visibleEndTime = viewportEndForChart,
                onViewportShift = if (isFullscreenMode && chartUiState.minChartTime != null && chartUiState.maxViewportStartTime != null) { deltaMs ->
                    val minChartTime = chartUiState.minChartTime!!
                    val maxViewportStart = chartUiState.maxViewportStartTime!!
                    val currentStart = (fullscreenViewportStartMs ?: minChartTime)
                        .coerceIn(minChartTime, maxViewportStart)
                    fullscreenViewportStartMs =
                        (currentStart + deltaMs).coerceIn(minChartTime, maxViewportStart)
                } else {
                    null
                }
            )
        }

        if (isChartFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                chartBlock(Modifier.fillMaxWidth(), true)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SplicedColumnGroup(title = typeLabel) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        InfoRow(
                            "时间",
                            "${formatDateTime(stats.startTime)} 到 ${formatDateTime(stats.endTime)} (${
                                formatDurationHours(durationMs)
                            })"
                        )
                        InfoRow(
                            "平均功率",
                            formatPower(stats.averagePower, dualCellEnabled, calibrationValue)
                        )
                        InfoRow("电量变化", "${capacityChange}%")
                        InfoRow("亮屏", formatDurationHours(stats.screenOnTimeMs))
                        InfoRow("息屏", formatDurationHours(stats.screenOffTimeMs))
                        InfoRow("记录ID", detail.name.dropLast(4))
                    }
                }
            }

            SplicedColumnGroup(title = "功耗/电量曲线") {
                item {
                    Column(modifier = Modifier.padding(12.dp)) {
                        chartBlock(Modifier.fillMaxWidth(), false)
                    }
                }
            }
        }
    }

    if (showGuideDialog) {
        ChartGuideDialog(
            onDismiss = { showGuideDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除记录") },
            text = { Text("删除后不可恢复，确认继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteRecord(context, recordsFile)
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun loadPowerCurveMode(value: String?): PowerCurveMode {
    // 缺省回到 Raw，而不是 Fitted：
    // 这样首次进入详情页时语义更接近旧版本的“功耗曲线”默认行为。
    return PowerCurveMode.entries.firstOrNull { it.name == value } ?: PowerCurveMode.Raw
}

private fun PowerCurveMode.next(): PowerCurveMode {
    // 图例点击按 Raw -> Fitted -> Hidden 循环切换，
    // 让“同一入口控制功耗线模式”比单独再做弹窗或多按钮更轻量。
    return when (this) {
        PowerCurveMode.Raw -> PowerCurveMode.Fitted
        PowerCurveMode.Fitted -> PowerCurveMode.Hidden
        PowerCurveMode.Hidden -> PowerCurveMode.Raw
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.3f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
