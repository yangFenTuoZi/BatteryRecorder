package yangfentuozi.batteryrecorder.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.ui.components.global.SwipeRevealRow
import yangfentuozi.batteryrecorder.ui.viewmodel.HistoryViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.formatDateTime
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatPower

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen(
    batteryStatus: BatteryStatus,
    viewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel,
    onNavigateToRecordDetail: (BatteryStatus, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    var openRecordName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(batteryStatus) {
        openRecordName = null
        viewModel.loadRecords(context, batteryStatus)
    }

    val title = if (batteryStatus == BatteryStatus.Charging) "充电历史" else "放电历史"

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
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    records.forEachIndexed { index, record ->
                        key(record.name) {
                            SwipeRevealRow(
                                isOpen = openRecordName == record.name,
                                onOpenChange = { open ->
                                    openRecordName = if (open) {
                                        record.name
                                    } else {
                                        if (openRecordName == record.name) null else openRecordName
                                    }
                                },
                                isGroupFirst = index == 0,
                                isGroupLast = index == records.size - 1,
                                actionContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    openRecordName = null
                                                    viewModel.deleteRecord(context, record.asRecordsFile())
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.onError
                                        )
                                    }
                                },
                                content = {
                                    val stats = record.stats
                                    val durationMs = stats.endTime - stats.startTime
                                    val capacityChange = if (record.type == BatteryStatus.Charging) {
                                        stats.endCapacity - stats.startCapacity
                                    } else {
                                        stats.startCapacity - stats.endCapacity
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
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
                                },
                                onContentClick = {
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
