package yangfentuozi.batteryrecorder.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.history.HistorySummary
import yangfentuozi.batteryrecorder.ui.format.formatPower

@Composable
fun ChargeStatsCard(
    summary: HistorySummary?,
    modifier: Modifier = Modifier,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Text(
            text = "充电总结",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (summary != null) {
            Spacer(Modifier.height(12.dp))

            StatRow("记录数", "${summary.recordCount} 次")

            StatRow(
                "平均功率", formatPower(
                    powerW = summary.averagePower,
                    dualCellEnabled = dualCellEnabled,
                    calibrationValue = calibrationValue
                )
            )

        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DischargeStatsCard(
    summary: HistorySummary?,
    modifier: Modifier = Modifier,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Text(
            text = "放电总结",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (summary != null) {
            Spacer(Modifier.height(12.dp))

            StatRow("记录数", "${summary.recordCount} 次")

            StatRow(
                "平均功率", formatPower(
                    powerW = summary.averagePower,
                    dualCellEnabled = dualCellEnabled,
                    calibrationValue = calibrationValue
                )
            )

        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
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
