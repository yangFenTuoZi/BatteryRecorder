package yangfentuozi.batteryrecorder.ui.compose.srceens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import yangfentuozi.batteryrecorder.ui.compose.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.HistoryViewModel
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.util.HistoryRecord
import yangfentuozi.batteryrecorder.util.RecordType
import yangfentuozi.batteryrecorder.util.formatDateTime
import yangfentuozi.batteryrecorder.util.formatDurationHours
import yangfentuozi.batteryrecorder.util.formatPower

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen(
    recordType: RecordType,
    viewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateToRecordDetail: (RecordType, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()

    LaunchedEffect(Unit) {
        settingsViewModel.init(context)
    }

    LaunchedEffect(recordType) {
        viewModel.loadRecords(context, recordType)
    }

    val title = if (recordType == RecordType.CHARGE) "充电历史" else "放电历史"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) }
            )
        }
    ) { paddingValues ->
        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp)
            ) {
                Text(
                    text = "暂无记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                SplicedColumnGroup(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    records.forEach { record ->
                        item(key = record.name) {
                            HistoryRecordItem(
                                record = record,
                                dualCellEnabled = dualCellEnabled,
                                calibrationValue = calibrationValue,
                                onClick = {
                                    onNavigateToRecordDetail(record.type, record.name)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HistoryRecordItem(
    record: HistoryRecord,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    onClick: () -> Unit
) {
    val stats = record.stats
    val durationMs = stats.endTime - stats.startTime
    val capacityChange = if (record.type == RecordType.CHARGE) {
        stats.endCapacity - stats.startCapacity
    } else {
        stats.startCapacity - stats.endCapacity
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(
            text = formatDateTime(stats.startTime),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "时长 ${formatDurationHours(durationMs)} · 电量 ${capacityChange}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "平均功率 ${formatPower(stats.averagePower, dualCellEnabled, calibrationValue)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
