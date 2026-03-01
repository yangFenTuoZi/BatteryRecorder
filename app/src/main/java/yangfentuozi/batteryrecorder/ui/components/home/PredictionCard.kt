package yangfentuozi.batteryrecorder.ui.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.history.PredictionResult
import yangfentuozi.batteryrecorder.data.history.SceneStats
import yangfentuozi.batteryrecorder.ui.components.global.StatRow
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatPower
import yangfentuozi.batteryrecorder.utils.formatRemainingTime

@Composable
fun PredictionCard(
    prediction: PredictionResult?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "续航预测",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))

        if (prediction == null || prediction.insufficientData) {
            Text(
                text = "数据不足",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatRow(
                label = "息屏",
                value = prediction.screenOffHours?.let { formatRemainingTime(it) } ?: "数据不足",
                modifier = Modifier.padding(vertical = 4.dp)
            )
            StatRow(
                label = "亮屏日常",
                value = prediction.screenOnDailyHours?.let { formatRemainingTime(it) } ?: "数据不足",
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun SceneStatsCard(
    sceneStats: SceneStats?,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "场景统计",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))

        if (sceneStats == null) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 息屏功耗（取绝对值显示）
            val offPowerText = if (sceneStats.screenOffTotalMs > 0) {
                val pw = formatPower(
                    kotlin.math.abs(sceneStats.screenOffAvgPowerNw),
                    dualCellEnabled, calibrationValue
                )
                val dur = formatDurationHours(sceneStats.screenOffTotalMs)
                "$pw（$dur）"
            } else "数据不足"

            StatRow(
                label = "息屏平均",
                value = offPowerText,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // 亮屏日常功耗
            val dailyPowerText = if (sceneStats.screenOnDailyTotalMs > 0) {
                val pw = formatPower(
                    kotlin.math.abs(sceneStats.screenOnDailyAvgPowerNw),
                    dualCellEnabled, calibrationValue
                )
                val dur = formatDurationHours(sceneStats.screenOnDailyTotalMs)
                "$pw（$dur）"
            } else "数据不足"

            StatRow(
                label = "亮屏日常",
                value = dailyPowerText,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = "预测基于典型日常功耗，高负载行为会显著缩短续航",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
