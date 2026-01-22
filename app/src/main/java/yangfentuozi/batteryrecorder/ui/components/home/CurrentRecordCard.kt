package yangfentuozi.batteryrecorder.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.RecordType
import yangfentuozi.batteryrecorder.ui.format.formatDateTime
import yangfentuozi.batteryrecorder.ui.format.formatDurationHours
import yangfentuozi.batteryrecorder.ui.format.formatPower

@Composable
fun CurrentRecordCard(
    record: HistoryRecord?,
    modifier: Modifier = Modifier,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clickable(enabled = record != null && onClick != null) { onClick?.invoke() }
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "当前记录" + if (record != null) {
                if (record.type == RecordType.CHARGE) {
                    " - 充电"
                } else {
                    " - 放电"
                }
            } else {
                ""
            },
            style = MaterialTheme.typography.titleMedium
        )

        if (record != null) {
            Spacer(Modifier.height(12.dp))
            val stats = record.stats

            StatRow(
                "开始时间",
                formatDateTime(stats.startTime)
            )
            StatRow(
                "平均功率", formatPower(
                    powerW = stats.averagePower,
                    dualCellEnabled = dualCellEnabled,
                    calibrationValue = calibrationValue
                )
            )
            val capacityChange = if (record.type == RecordType.CHARGE) {
                stats.endCapacity - stats.startCapacity
            } else {
                stats.startCapacity - stats.endCapacity
            }
            StatRow("电量变化", "${capacityChange}%")
            StatRow("时长", formatDurationHours(stats.endTime - stats.startTime))
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "暂无记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
