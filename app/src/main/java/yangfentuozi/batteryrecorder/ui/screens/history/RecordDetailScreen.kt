package yangfentuozi.batteryrecorder.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import yangfentuozi.batteryrecorder.data.history.RecordType
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCapacityChart
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.viewmodel.HistoryViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.formatDateTime
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatPower
import yangfentuozi.batteryrecorder.utils.formatPowerInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordType: RecordType,
    recordName: String,
    viewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val record by viewModel.recordDetail.collectAsState()
    val points by viewModel.recordPoints.collectAsState()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val recordScreenOffEnabled by settingsViewModel.recordScreenOff.collectAsState()

    LaunchedEffect(Unit) {
        settingsViewModel.init(context)
    }

    LaunchedEffect(recordType, recordName) {
        viewModel.loadRecord(context, recordType, recordName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录详情") }
            )
        }
    ) { paddingValues ->
        val detail = record
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (detail == null) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val stats = detail.stats
                val durationMs = stats.endTime - stats.startTime
                val capacityChange = if (detail.type == RecordType.CHARGE) {
                    stats.endCapacity - stats.startCapacity
                } else {
                    stats.startCapacity - stats.endCapacity
                }
                val typeLabel = if (detail.type == RecordType.CHARGE) "充电记录" else "放电记录"

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
                            InfoRow("文件名", detail.name)
                        }
                    }
                }

                SplicedColumnGroup(title = "功耗/电量曲线") {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val chartPoints = if (detail.type == RecordType.CHARGE) {
                                points.map { it.copy(power = it.power) }
                            } else {
                                points
                            }
                            PowerCapacityChart(
                                points = chartPoints,
                                recordScreenOffEnabled = recordScreenOffEnabled,
                                modifier = Modifier.fillMaxWidth(),
                                powerLabelFormatter = { value ->
                                    formatPower(value, dualCellEnabled, calibrationValue)
                                },
                                capacityLabelFormatter = { value -> "$value%" },
                                timeLabelFormatter = { value ->
                                    val offset = (value - stats.startTime).coerceAtLeast(0L)
                                    formatRelativeOffset(offset)
                                },
                                axisPowerLabelFormatter = { value ->
                                    formatPowerInt(value, dualCellEnabled, calibrationValue)
                                },
                                axisCapacityLabelFormatter = { value -> "$value%" },
                                axisTimeLabelFormatter = { value -> formatRelativeOffset(value) }
                            )
                        }
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
