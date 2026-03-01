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
import yangfentuozi.batteryrecorder.utils.computePowerW
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatRemainingTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

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
    dischargeDisplayPositive: Boolean,
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
            // 息屏功耗（先转瓦特再根据设置取绝对值，避免 calibrationValue 符号干扰）
            val offPowerText = if (sceneStats.screenOffTotalMs > 0) {
                var w = computePowerW(sceneStats.screenOffAvgPowerNw, dualCellEnabled, calibrationValue)
                if (dischargeDisplayPositive) w = kotlin.math.abs(w)
                val rawDur = formatDurationHours(sceneStats.screenOffTotalMs)
                val effMs = sceneStats.screenOffEffectiveTotalMs.roundToLong().coerceAtLeast(0L)
                val effDur = formatDurationHours(effMs)
                val durText = if (abs(effMs - sceneStats.screenOffTotalMs) >= 10_000L) {
                    "记录 $rawDur，加权 $effDur"
                } else rawDur
                "${String.format(Locale.getDefault(), "%.2f W", w)}（$durText）"
            } else "数据不足"

            StatRow(
                label = "息屏平均",
                value = offPowerText,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // 亮屏日常功耗
            val dailyPowerText = if (sceneStats.screenOnDailyTotalMs > 0) {
                var w = computePowerW(sceneStats.screenOnDailyAvgPowerNw, dualCellEnabled, calibrationValue)
                if (dischargeDisplayPositive) w = kotlin.math.abs(w)
                val rawDur = formatDurationHours(sceneStats.screenOnDailyTotalMs)
                val effMs = sceneStats.screenOnDailyEffectiveTotalMs.roundToLong().coerceAtLeast(0L)
                val effDur = formatDurationHours(effMs)
                val durText = if (abs(effMs - sceneStats.screenOnDailyTotalMs) >= 10_000L) {
                    "记录 $rawDur，加权 $effDur"
                } else rawDur
                "${String.format(Locale.getDefault(), "%.2f W", w)}（$durText）"
            } else "数据不足"

            StatRow(
                label = "亮屏日常",
                value = dailyPowerText,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = "开启当次记录加权后，平均功耗按时间衰减加权计算",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
