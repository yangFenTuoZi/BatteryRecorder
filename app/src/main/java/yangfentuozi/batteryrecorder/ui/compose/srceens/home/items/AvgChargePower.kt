package yangfentuozi.batteryrecorder.ui.compose.srceens.home.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.util.PowerStats

@Composable
fun ChargeStatsCard(
    stats: PowerStats?,
    modifier: Modifier = Modifier,
    dualCellEnabled: Boolean,
    calibrationValue: Int
) {
    Card(
        modifier = modifier.padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "充电统计",
                style = MaterialTheme.typography.titleMedium
            )

            if (stats != null) {
                Spacer(Modifier.height(12.dp))

                // 平均功率
                StatRow(
                    "平均功率", formatPower(
                        powerW = stats.averagePower,
                        dualCellEnabled = dualCellEnabled,
                        calibrationValue = calibrationValue
                    )
                )

                // 电量变化
                val capacityChange = stats.endCapacity - stats.startCapacity
                StatRow("电量变化", "$capacityChange%")

                // 时长
                val durationHours = (stats.endTime - stats.startTime) / 3600000.0
                StatRow("时长", String.format("%.1fh", durationHours))

                // 亮屏时间
                val screenOnHours = stats.screenOnTimeMs / 3600000.0
                StatRow("亮屏", String.format("%.1fh", screenOnHours))

                // 息屏时间
                val screenOffHours = stats.screenOffTimeMs / 3600000.0
                StatRow("息屏", String.format("%.1fh", screenOffHours))

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
}

@Composable
fun DischargeStatsCard(
    stats: PowerStats?,
    modifier: Modifier = Modifier,
    dualCellEnabled: Boolean,
    calibrationValue: Int
) {
    Card(
        modifier = modifier.padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "放电统计",
                style = MaterialTheme.typography.titleMedium
            )

            if (stats != null) {
                Spacer(Modifier.height(12.dp))

                // 平均功率
                StatRow(
                    "平均功率", formatPower(
                        powerW = stats.averagePower,
                        dualCellEnabled = dualCellEnabled,
                        calibrationValue = calibrationValue
                    )
                )

                // 电量变化
                val capacityChange = stats.startCapacity - stats.endCapacity
                StatRow("电量消耗", "$capacityChange%")

                // 时长
                val durationHours = (stats.endTime - stats.startTime) / 3600000.0
                StatRow("时长", String.format("%.1fh", durationHours))

                // 亮屏时间
                val screenOnHours = stats.screenOnTimeMs / 3600000.0
                StatRow("亮屏", String.format("%.1fh", screenOnHours))

                // 息屏时间
                val screenOffHours = stats.screenOffTimeMs / 3600000.0
                StatRow("息屏", String.format("%.1fh", screenOffHours))

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

private fun formatPower(
    powerW: Double,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    val finalValue = (if (dualCellEnabled) 2 else 1) * calibrationValue * (powerW / 1000000)
    return String.format("%.1f W", finalValue)
}

