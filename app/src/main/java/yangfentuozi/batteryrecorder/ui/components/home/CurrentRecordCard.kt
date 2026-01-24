package yangfentuozi.batteryrecorder.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.RecordType
import yangfentuozi.batteryrecorder.server.BatteryStatus
import yangfentuozi.batteryrecorder.ui.viewmodel.LivePowerPoint
import yangfentuozi.batteryrecorder.utils.formatDateTime
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatPower
import kotlin.math.max

@Composable
fun CurrentRecordCard(
    record: HistoryRecord?,
    modifier: Modifier = Modifier,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    livePoints: List<LivePowerPoint>,
    dischargeDisplayPositive: Boolean,
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
            val latestPoint = livePoints.lastOrNull()
            val latestPower = latestPoint?.powerNw
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                    Spacer(Modifier.height(16.dp))
                    StatRow(
                        "当前功耗", if (latestPower != null) formatPower(
                            powerW = applyDischargeSignForDisplay(
                                rawPowerNw = latestPower.toDouble(),
                                status = latestPoint.status,
                                dischargeDisplayPositive = dischargeDisplayPositive
                            ),
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = calibrationValue
                        ) else "--W"
                    )
                }

                LivePowerChart(
                    points = livePoints,
                    dualCellEnabled = dualCellEnabled,
                    calibrationValue = calibrationValue,
                    dischargeDisplayPositive = dischargeDisplayPositive,
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp)
                )
            }
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
            .fillMaxWidth(),
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

@Composable
private fun LivePowerChart(
    points: List<LivePowerPoint>,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    dischargeDisplayPositive: Boolean,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Box(modifier = modifier) {
        if (points.size < 2) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val displayPoints = run {
                val multiplier = if (dualCellEnabled) 2 else 1
                points.map {
                    val powerW = multiplier * calibrationValue * (it.powerNw / 1_000_000_000.0)
                    val plotPowerW = applyDischargeSignForPlot(powerW, it.status)
                    LivePowerPointDisplay(it.timestamp, plotPowerW, it.isGap)
                }.sortedBy { it.timestamp }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = 6.dp.toPx()
                val minTime = displayPoints.first().timestamp
                val maxTime = displayPoints.last().timestamp
                val timeRange = max(1L, maxTime - minTime).toDouble()

                val minPower = displayPoints.minOf { it.power }
                val maxPower = displayPoints.maxOf { it.power }
                val powerRange = max(1e-6, maxPower - minPower)

                val rows = 4
                val cols = 4
                val labelTextSize = 22f
                val labelPaint = createLiveChartTextPaint(gridColor.toArgb(), labelTextSize)
                val labelPadding = 6.dp.toPx()
                val isDischarging = points.lastOrNull()?.status == BatteryStatus.Discharging.value
                val labelSignMultiplier = if (isDischarging && !dischargeDisplayPositive) -1 else 1
                val labelTexts = (0..rows).map { index ->
                    val value = (maxPower - (powerRange * index / rows)) * labelSignMultiplier
                    String.format("%.1f W", value)
                }
                val labelWidth = labelTexts.maxOf { labelPaint.measureText(it) }

                val left = padding + labelWidth + labelPadding
                val top = padding
                val right = size.width - padding
                val bottom = size.height - padding
                val chartWidth = right - left
                val chartHeight = bottom - top
                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                val rowStep = chartHeight / rows
                val colStep = chartWidth / cols
                val gridStroke = 1.dp.toPx()
                val gridLineColor = gridColor.copy(alpha = 0.3f)

                for (i in 0..rows) {
                    val y = top + rowStep * i
                    drawLine(
                        color = gridLineColor,
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = gridStroke
                    )
                    val label = labelTexts[i]
                    val labelX = left - labelPadding - labelPaint.measureText(label)
                    val labelY = y - 4.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, labelPaint)
                }
                for (i in 0..cols) {
                    val x = left + colStep * i
                    drawLine(
                        color = gridLineColor,
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = gridStroke
                    )
                }

                drawRect(
                    color = gridColor.copy(alpha = 0.6f),
                    topLeft = Offset(left, top),
                    size = Size(chartWidth, chartHeight),
                    style = Stroke(width = 1.dp.toPx())
                )

                val strokeWidth = 2.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                for (index in 1 until displayPoints.size) {
                    val previous = displayPoints[index - 1]
                    val current = displayPoints[index]
                    val startX = left + ((previous.timestamp - minTime) / timeRange).toFloat() * chartWidth
                    val startY = top + (1f - ((previous.power - minPower) / powerRange).toFloat()) * chartHeight
                    val endX = left + ((current.timestamp - minTime) / timeRange).toFloat() * chartWidth
                    val endY = top + (1f - ((current.power - minPower) / powerRange).toFloat()) * chartHeight
                    val segmentPath = Path().apply {
                        moveTo(startX, startY)
                        lineTo(endX, endY)
                    }
                    val isGapSegment = previous.isGap || current.isGap
                    drawPath(
                        path = segmentPath,
                        color = lineColor,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (isGapSegment) dashEffect else null
                        )
                    )
                }
            }
        }
    }
}

private data class LivePowerPointDisplay(
    val timestamp: Long,
    val power: Double,
    val isGap: Boolean
)

private fun applyDischargeSignForDisplay(
    rawPowerNw: Double,
    status: Int?,
    dischargeDisplayPositive: Boolean
): Double {
    if (status == BatteryStatus.Discharging.value) {
        val absPower = kotlin.math.abs(rawPowerNw)
        return if (dischargeDisplayPositive) -absPower else absPower
    }
    return rawPowerNw
}

private fun applyDischargeSignForPlot(rawPowerW: Double, status: Int?): Double {
    return if (status == BatteryStatus.Discharging.value) kotlin.math.abs(rawPowerW) else rawPowerW
}

private fun createLiveChartTextPaint(color: Int, textSizePx: Float) =
    android.graphics.Paint().apply {
        this.color = color
        this.textSize = textSizePx
        isAntiAlias = true
    }
