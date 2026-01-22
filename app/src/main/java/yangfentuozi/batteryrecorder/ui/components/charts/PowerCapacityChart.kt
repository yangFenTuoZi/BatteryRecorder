package yangfentuozi.batteryrecorder.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PowerCapacityChart(
    points: List<ChartPoint>,
    recordScreenOffEnabled: Boolean,
    modifier: Modifier = Modifier,
    powerColor: Color = MaterialTheme.colorScheme.primary,
    capacityColor: Color = MaterialTheme.colorScheme.tertiary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    strokeWidth: Dp = 2.dp,
    screenOnColor: Color = Color(0xFF2E7D32),
    screenOffColor: Color = Color(0xFFD32F2F),
    showScreenStateLine: Boolean = true,
    powerLabelFormatter: (Double) -> String = { value -> String.format("%.1f", value) },
    capacityLabelFormatter: (Int) -> String = { value -> "$value%" },
    timeLabelFormatter: (Long) -> String = { value -> value.toString() },
    axisPowerLabelFormatter: (Double) -> String = { value -> value.roundToInt().toString() },
    axisCapacityLabelFormatter: (Int) -> String = { value -> "$value%" },
    axisTimeLabelFormatter: (Long) -> String = { value -> formatRelativeTime(value) }
) {
    val filteredPoints = normalizePoints(points, recordScreenOffEnabled)
    val rawPoints = points.sortedBy { it.timestamp }
    val selectedPointState = remember { mutableStateOf<ChartPoint?>(null) }

    Column(modifier = modifier) {
        if (filteredPoints.size < 2) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        SelectedPointInfo(
            selected = selectedPointState.value,
            timeLabelFormatter = timeLabelFormatter,
            powerLabelFormatter = powerLabelFormatter,
            capacityLabelFormatter = capacityLabelFormatter
        )

        Spacer(modifier = Modifier.height(13.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .pointerInput(filteredPoints) {
                        detectTapGestures { offset ->
                            val paddingLeft = 32.dp.toPx()
                            val paddingRight = 32.dp.toPx()
                            val chartWidth = size.width - paddingLeft - paddingRight
                            if (chartWidth <= 0f) return@detectTapGestures
                            val minTime = filteredPoints.minOf { it.timestamp }
                            val maxTime = filteredPoints.maxOf { it.timestamp }
                            val timeRange = max(1L, maxTime - minTime).toDouble()
                            val x = (offset.x - paddingLeft).coerceIn(0f, chartWidth)
                            val targetTime = minTime + (x / chartWidth * timeRange).toLong()
                            selectedPointState.value = filteredPoints.minByOrNull {
                                kotlin.math.abs(it.timestamp - targetTime)
                            }
                        }
                    }
                    .pointerInput(filteredPoints) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val paddingLeft = 32.dp.toPx()
                            val paddingRight = 32.dp.toPx()
                            val chartWidth = size.width - paddingLeft - paddingRight
                            if (chartWidth <= 0f) return@detectDragGestures
                            val minTime = filteredPoints.minOf { it.timestamp }
                            val maxTime = filteredPoints.maxOf { it.timestamp }
                            val timeRange = max(1L, maxTime - minTime).toDouble()
                            val x = (change.position.x - paddingLeft).coerceIn(0f, chartWidth)
                            val targetTime = minTime + (x / chartWidth * timeRange).toLong()
                            selectedPointState.value = filteredPoints.minByOrNull {
                                kotlin.math.abs(it.timestamp - targetTime)
                            }
                        }
                    }
            ) {
                val paddingLeft = 32.dp.toPx()
                val paddingRight = 32.dp.toPx()
                val paddingTop = 6.dp.toPx()
                val paddingBottom = 24.dp.toPx()
                val chartWidth = size.width - paddingLeft - paddingRight
                val chartHeight = size.height - paddingTop - paddingBottom
                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                val minTime = filteredPoints.minOf { it.timestamp }
                val maxTime = filteredPoints.maxOf { it.timestamp }
                val minPower = filteredPoints.minOf { it.power }
                val maxPower = filteredPoints.maxOf { it.power }
                val minCapacity = 0.0
                val maxCapacity = 100.0

                val powerPath = buildPath(
                    points = filteredPoints,
                    minTime = minTime,
                    maxTime = maxTime,
                    minValue = minPower,
                    maxValue = maxPower,
                    paddingLeft = paddingLeft,
                    paddingTop = paddingTop,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    valueSelector = { it.power }
                )
                val capacityPath = buildPath(
                    points = filteredPoints,
                    minTime = minTime,
                    maxTime = maxTime,
                    minValue = minCapacity,
                    maxValue = maxCapacity,
                    paddingLeft = paddingLeft,
                    paddingTop = paddingTop,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    chartHeightScale = 0.9f,
                    valueSelector = { it.capacity.toDouble().coerceIn(minCapacity, maxCapacity) }
                )

                drawGrid(
                    paddingLeft = paddingLeft,
                    paddingTop = paddingTop,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    gridColor = gridColor
                )

                drawAxisLabels(
                    paddingLeft = paddingLeft,
                    paddingTop = paddingTop,
                    paddingRight = paddingRight,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    minTime = minTime,
                    maxTime = maxTime,
                    minPower = minPower,
                    maxPower = maxPower,
                    minCapacity = minCapacity,
                    maxCapacity = maxCapacity,
                    gridColor = gridColor,
                    powerLabelFormatter = axisPowerLabelFormatter,
                    capacityLabelFormatter = axisCapacityLabelFormatter,
                    timeLabelFormatter = axisTimeLabelFormatter
                )

                drawPath(
                    path = powerPath,
                    color = powerColor,
                    style = Stroke(
                        width = strokeWidth.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                drawPath(
                    path = capacityPath,
                    color = capacityColor,
                    style = Stroke(
                        width = strokeWidth.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                if (showScreenStateLine && rawPoints.isNotEmpty()) {
                    drawScreenStateLine(
                        points = rawPoints,
                        minTime = minTime,
                        maxTime = maxTime,
                        paddingLeft = paddingLeft,
                        paddingTop = paddingTop,
                        chartWidth = chartWidth,
                        chartHeight = chartHeight,
                        screenOnColor = screenOnColor,
                        screenOffColor = screenOffColor,
                        strokeWidth = 4.dp
                    )
                }

                selectedPointState.value?.let { selectedPoint ->
                    val timeRange = max(1L, maxTime - minTime).toDouble()
                    val selectedX = paddingLeft +
                            ((selectedPoint.timestamp - minTime) / timeRange).toFloat() * chartWidth
                    val powerY = mapToY(
                        value = selectedPoint.power,
                        minValue = minPower,
                        maxValue = maxPower,
                        paddingTop = paddingTop,
                        chartHeight = chartHeight
                    )
                    val capacityY = mapToY(
                        value = selectedPoint.capacity.toDouble(),
                        minValue = minCapacity,
                        maxValue = maxCapacity,
                        paddingTop = paddingTop,
                        chartHeight = chartHeight * 0.9f
                    )
                    drawLine(
                        color = gridColor.copy(alpha = 0.6f),
                        start = Offset(selectedX, paddingTop),
                        end = Offset(selectedX, paddingTop + chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(powerColor, radius = 4.dp.toPx(), center = Offset(selectedX, powerY))
                    drawCircle(
                        capacityColor,
                        radius = 4.dp.toPx(),
                        center = Offset(selectedX, capacityY)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendItem(label = "功耗", color = powerColor)
            LegendItem(label = "电量", color = capacityColor)
            if (showScreenStateLine) {
                LegendItem(label = "亮屏线", color = screenOnColor)
                LegendItem(label = "息屏线", color = screenOffColor)
            }
        }
    }
}

@Composable
private fun SelectedPointInfo(
    selected: ChartPoint?,
    timeLabelFormatter: (Long) -> String,
    powerLabelFormatter: (Double) -> String,
    capacityLabelFormatter: (Int) -> String
) {
    val text = if (selected == null) {
        "点击图表查看该时间点数据"
    } else {
        "时间 ${timeLabelFormatter(selected.timestamp)} · 功耗 ${powerLabelFormatter(selected.power)} · 电量 ${capacityLabelFormatter(selected.capacity)}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun normalizePoints(
    points: List<ChartPoint>,
    recordScreenOffEnabled: Boolean
): List<ChartPoint> {
    if (recordScreenOffEnabled) return points
    if (points.size < 3) return points

    val sorted = points.sortedBy { it.timestamp }
    val result = ArrayList<ChartPoint>(sorted.size)
    for (index in sorted.indices) {
        val current = sorted[index]
        if (!current.isDisplayOn) {
            val prev = sorted.getOrNull(index - 1)
            val next = sorted.getOrNull(index + 1)
            if (prev?.isDisplayOn == true && next?.isDisplayOn == true) {
                continue
            }
        }
        result.add(current)
    }
    return result
}

private fun buildPath(
    points: List<ChartPoint>,
    minTime: Long,
    maxTime: Long,
    minValue: Double,
    maxValue: Double,
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    chartHeightScale: Float = 1f,
    valueSelector: (ChartPoint) -> Double
): Path {
    val path = Path()
    val timeRange = max(1L, maxTime - minTime).toDouble()
    val valueRange = max(1e-6, maxValue - minValue)

    points.forEachIndexed { index, point ->
        val x = paddingLeft + ((point.timestamp - minTime) / timeRange).toFloat() * chartWidth
        val normalized = ((valueSelector(point) - minValue) / valueRange).toFloat()
        val y = paddingTop + (1f - normalized) * chartHeight * chartHeightScale

        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    return path
}

private fun mapToY(
    value: Double,
    minValue: Double,
    maxValue: Double,
    paddingTop: Float,
    chartHeight: Float
): Float {
    val valueRange = max(1e-6, maxValue - minValue)
    val normalized = ((value - minValue) / valueRange).toFloat()
    return paddingTop + (1f - normalized) * chartHeight
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    gridColor: Color
) {
    val rows = 4
    val cols = 4
    val rowStep = chartHeight / rows
    val colStep = chartWidth / cols

    for (i in 0..rows) {
        val y = paddingTop + rowStep * i
        drawLine(
            color = gridColor.copy(alpha = 0.3f),
            start = Offset(paddingLeft, y),
            end = Offset(paddingLeft + chartWidth, y),
            strokeWidth = 1.dp.toPx()
        )
    }
    for (i in 0..cols) {
        val x = paddingLeft + colStep * i
        drawLine(
            color = gridColor.copy(alpha = 0.3f),
            start = Offset(x, paddingTop),
            end = Offset(x, paddingTop + chartHeight),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScreenStateLine(
    points: List<ChartPoint>,
    minTime: Long,
    maxTime: Long,
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    screenOnColor: Color,
    screenOffColor: Color,
    strokeWidth: Dp
) {
    val timeRange = max(1L, maxTime - minTime).toDouble()
    val y = paddingTop + chartHeight + 8.dp.toPx()

    if (points.isEmpty()) return

    if (points.size == 1) {
        drawLine(
            color = if (points[0].isDisplayOn) screenOnColor else screenOffColor,
            start = Offset(paddingLeft, y),
            end = Offset(paddingLeft + chartWidth, y),
            strokeWidth = strokeWidth.toPx()
        )
        return
    }

    val screenOnPath = Path()
    val screenOffPath = Path()
    var onPathStarted = false
    var offPathStarted = false

    for (i in 0 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        val startX = paddingLeft + ((current.timestamp - minTime) / timeRange).toFloat() * chartWidth
        val endX = paddingLeft + ((next.timestamp - minTime) / timeRange).toFloat() * chartWidth

        if (current.isDisplayOn) {
            if (!onPathStarted) {
                screenOnPath.moveTo(startX, y)
                onPathStarted = true
            }
            screenOnPath.lineTo(startX, y)
            screenOnPath.lineTo(endX, y)
        } else {
            if (!offPathStarted) {
                screenOffPath.moveTo(startX, y)
                offPathStarted = true
            }
            screenOffPath.lineTo(startX, y)
            screenOffPath.lineTo(endX, y)
        }
    }

    if (onPathStarted) {
        drawPath(
            path = screenOnPath,
            color = screenOnColor,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
        )
    }
    if (offPathStarted) {
        drawPath(
            path = screenOffPath,
            color = screenOffColor,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxisLabels(
    paddingLeft: Float,
    paddingTop: Float,
    paddingRight: Float,
    chartWidth: Float,
    chartHeight: Float,
    minTime: Long,
    maxTime: Long,
    minPower: Double,
    maxPower: Double,
    minCapacity: Double,
    maxCapacity: Double,
    gridColor: Color,
    powerLabelFormatter: (Double) -> String,
    capacityLabelFormatter: (Int) -> String,
    timeLabelFormatter: (Long) -> String
) {
    val textPaint = android.graphics.Paint().apply {
        color = gridColor.toArgb()
        textSize = 20f
        isAntiAlias = true
    }
    val rows = 4
    val cols = 3
    val rowStep = chartHeight / rows
    val colStep = chartWidth / cols
    val powerRange = max(1e-6, maxPower - minPower)
    val capacityRange = max(1e-6, maxCapacity - minCapacity)
    val timeRange = max(1L, maxTime - minTime).toDouble()

    for (i in 0..rows) {
        val y = paddingTop + rowStep * i
        val powerValue = maxPower - (powerRange * i / rows)
        val capacityValue = maxCapacity - (capacityRange * i / rows)
        val powerText = powerLabelFormatter(powerValue)
        val powerWidth = textPaint.measureText(powerText)
        drawContext.canvas.nativeCanvas.drawText(
            powerText,
            paddingLeft - powerWidth - 8.dp.toPx(),
            y - 4.dp.toPx(),
            textPaint
        )
        val capacityText = capacityLabelFormatter(capacityValue.roundToInt())
        drawContext.canvas.nativeCanvas.drawText(
            capacityText,
            paddingLeft + chartWidth + 8.dp.toPx(),
            y - 4.dp.toPx(),
            textPaint
        )
    }

    for (i in 0..cols) {
        val x = paddingLeft + colStep * i
        val timeValue = (timeRange * i / cols).toLong()
        val text = timeLabelFormatter(timeValue)
        val textWidth = textPaint.measureText(text)
        drawContext.canvas.nativeCanvas.drawText(
            text,
            x - textWidth / 2f,
            paddingTop + chartHeight + 24.dp.toPx(),
            textPaint
        )
    }
}

private fun formatRelativeTime(offsetMs: Long): String {
    val totalMinutes = max(0, (offsetMs / 60000L).toInt())
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
