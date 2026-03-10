package yangfentuozi.batteryrecorder.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.history.PredictionResult
import yangfentuozi.batteryrecorder.data.history.SceneStats
import yangfentuozi.batteryrecorder.ui.components.global.StatRow
import yangfentuozi.batteryrecorder.utils.computePowerW
import yangfentuozi.batteryrecorder.utils.formatFullRemainingTime
import yangfentuozi.batteryrecorder.utils.formatRemainingTime

/**
 * 首页卡片统一显示“当前电量 / 满电”两种口径，缺数据时保持一致文案。
 */
private fun formatPredictionPair(
    currentHours: Double?,
    fullHours: Double?
): String {
    val currentText = currentHours?.let(::formatRemainingTime) ?: "数据不足"
    val fullText = fullHours?.let(::formatFullRemainingTime) ?: "数据不足"
    return "$currentText / $fullText"
}

@Composable
fun PredictionCard(
    prediction: PredictionResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 卡片整体可点击，入口语义是进入更细的应用预测详情页。
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // 特判置信度 100 为服务未启动情况
        val title = if (prediction != null && !prediction.insufficientData && prediction.confidenceScore != 100)
            "续航预测 - 置信评分 ${prediction.confidenceScore}"
        else "续航预测"
        Text(
            text = title,
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
                value = formatPredictionPair(
                    currentHours = prediction.screenOffCurrentHours,
                    fullHours = prediction.screenOffFullHours
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            StatRow(
                label = "亮屏日常",
                value = formatPredictionPair(
                    currentHours = prediction.screenOnDailyCurrentHours,
                    fullHours = prediction.screenOnDailyFullHours
                ),
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
            // 先按统一公式转瓦特，再在展示层决定是否将放电视为正值。
            val offPowerText = if (sceneStats.screenOffTotalMs > 0) {
                var w = computePowerW(sceneStats.screenOffAvgPowerRaw, dualCellEnabled, calibrationValue)
                if (dischargeDisplayPositive) w = kotlin.math.abs(w)
                String.format(LocalLocale.current.platformLocale, "%.2f W", w)
            } else "数据不足"

            StatRow(
                label = "息屏平均",
                value = offPowerText,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Locale 走 Compose 当前上下文，避免与系统配置更新不同步。
            val dailyPowerText = if (sceneStats.screenOnDailyTotalMs > 0) {
                var w = computePowerW(sceneStats.screenOnDailyAvgPowerRaw, dualCellEnabled, calibrationValue)
                if (dischargeDisplayPositive) w = kotlin.math.abs(w)
                String.format(LocalLocale.current.platformLocale, "%.2f W", w)
            } else "数据不足"

            StatRow(
                label = "亮屏日常",
                value = dailyPowerText,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
