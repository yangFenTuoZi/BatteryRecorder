package yangfentuozi.batteryrecorder.ui.screens.history

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.compose.viewModel
import yangfentuozi.batteryrecorder.data.history.RecordType
import yangfentuozi.batteryrecorder.ui.components.charts.FixedPowerAxisMode
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCapacityChart
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.viewmodel.HistoryViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.formatDateTime
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatPower
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordType: RecordType,
    recordName: String,
    viewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val record by viewModel.recordDetail.collectAsState()
    val points by viewModel.recordPoints.collectAsState()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val dischargeDisplayPositive by settingsViewModel.dischargeDisplayPositive.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val recordScreenOffEnabled by settingsViewModel.screenOffRecord.collectAsState()
    var isChartFullscreen by rememberSaveable(recordType, recordName) { mutableStateOf(false) }
    var fullscreenViewportStartMs by rememberSaveable(recordType, recordName) { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        settingsViewModel.init(context)
    }

    LaunchedEffect(recordType, recordName) {
        viewModel.loadRecord(context, recordType, recordName)
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
                    title = { Text("记录详情") }
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
        val capacityChange = if (detail.type == RecordType.CHARGE) {
            stats.endCapacity - stats.startCapacity
        } else {
            stats.startCapacity - stats.endCapacity
        }
        val typeLabel = if (detail.type == RecordType.CHARGE) "充电记录" else "放电记录"
        val cellMultiplier = if (dualCellEnabled) 2 else 1
        val chartPoints = points.map { point ->
            val displayPowerW = cellMultiplier * calibrationValue * (point.power / 1000000000.0)
            val powerForChart = if (detail.type == RecordType.CHARGE && displayPowerW < 0) {
                0.0
            } else {
                displayPowerW
            }
            point.copy(power = powerForChart)
        }
        val minChartTime = chartPoints.minOfOrNull { it.timestamp }
        val maxChartTime = chartPoints.maxOfOrNull { it.timestamp }
        val totalDurationMs = if (minChartTime != null && maxChartTime != null) {
            (maxChartTime - minChartTime).coerceAtLeast(1L)
        } else {
            0L
        }
        val viewportDurationMs = if (totalDurationMs > 0L) {
            (totalDurationMs * 0.25).roundToLong().coerceAtLeast(1L)
        } else {
            0L
        }
        val maxViewportStart = if (minChartTime != null && maxChartTime != null) {
            (maxChartTime - viewportDurationMs).coerceAtLeast(minChartTime)
        } else {
            null
        }
        val viewportStartForChart = if (isChartFullscreen && minChartTime != null) {
            val initialStart = fullscreenViewportStartMs ?: minChartTime
            if (maxViewportStart == null) {
                initialStart
            } else {
                initialStart.coerceIn(minChartTime, maxViewportStart)
            }
        } else {
            null
        }
        val viewportEndForChart = if (isChartFullscreen &&
            viewportStartForChart != null &&
            maxChartTime != null
        ) {
            (viewportStartForChart + viewportDurationMs).coerceAtMost(maxChartTime)
        } else {
            null
        }

        val toggleFullscreen: () -> Unit = {
            if (isChartFullscreen) {
                isChartFullscreen = false
                fullscreenViewportStartMs = null
            } else {
                isChartFullscreen = true
                fullscreenViewportStartMs = minChartTime
            }
        }

        val fixedPowerMode = if (detail.type == RecordType.DISCHARGE && !dischargeDisplayPositive) {
            FixedPowerAxisMode.NegativeOnly
        } else {
            FixedPowerAxisMode.PositiveOnly
        }

        val chartBlock: @Composable (Modifier, Boolean) -> Unit = { modifier, isFullscreenMode ->
            PowerCapacityChart(
                points = chartPoints,
                recordScreenOffEnabled = recordScreenOffEnabled,
                modifier = modifier,
                useFixedPowerAxisSegments = true,
                fixedPowerAxisMode = fixedPowerMode,
                showCapacityAxis = false,
                showCapacityMarkers = true,
                showPeakPowerLine = true,
                chartHeight = if (isFullscreenMode) 320.dp else 240.dp,
                showFullscreenToggle = true,
                isFullscreen = isFullscreenMode,
                onToggleFullscreen = toggleFullscreen,
                useFivePercentTimeGrid = isFullscreenMode,
                visibleStartTime = viewportStartForChart,
                visibleEndTime = viewportEndForChart,
                onViewportShift = if (isFullscreenMode && minChartTime != null && maxViewportStart != null) { deltaMs ->
                    val currentStart = (fullscreenViewportStartMs ?: minChartTime)
                        .coerceIn(minChartTime, maxViewportStart)
                    val shiftedStart = (currentStart + deltaMs).coerceIn(minChartTime, maxViewportStart)
                    fullscreenViewportStartMs = shiftedStart
                } else {
                    null
                },
                powerLabelFormatter = { value ->
                    String.format(Locale.getDefault(), "%.2f W", value)
                },
                capacityLabelFormatter = { value -> "$value%" },
                tempLabelFormatter = { value ->
                    String.format(Locale.getDefault(), "%.1f ℃", value / 10.0)
                },
                timeLabelFormatter = { value ->
                    val offset = (value - stats.startTime).coerceAtLeast(0L)
                    formatRelativeOffset(offset)
                },
                axisPowerLabelFormatter = { value ->
                    String.format(Locale.getDefault(), "%.0f W", value)
                },
                axisCapacityLabelFormatter = { value -> "$value%" },
                axisTimeLabelFormatter = { value ->
                    val offset = (value - stats.startTime).coerceAtLeast(0L)
                    formatRelativeOffset(offset)
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
}

private fun formatRelativeOffset(offsetMs: Long): String {
    val totalMinutes = (offsetMs / 60000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        if (minutes == 0) {
            "${hours}h"
        } else {
            "${hours}h${minutes}m"
        }
    } else {
        "${minutes}m"
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
