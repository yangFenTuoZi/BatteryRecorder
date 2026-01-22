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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    strokeWidth: Dp = 1.3.dp,
    screenOnColor: Color = Color(0xFF2E7D32),
    screenOffColor: Color = Color(0xFFD32F2F),
    peakLineColor: Color = MaterialTheme.colorScheme.error,
    showPeakPowerLine: Boolean = false,
    showScreenStateLine: Boolean = true,
    useFixedPowerAxisSegments: Boolean = false,
    fixedPowerAxisMode: FixedPowerAxisMode = FixedPowerAxisMode.PositiveOnly,
    showCapacityAxis: Boolean = true,
    showCapacityMarkers: Boolean = false,
    powerLabelFormatter: (Double) -> String = { value -> String.format("%.1f", value) },
    capacityLabelFormatter: (Int) -> String = { value -> "$value%" },
    timeLabelFormatter: (Long) -> String = { value -> value.toString() },
    axisPowerLabelFormatter: (Double) -> String = { value -> value.roundToInt().toString() },
    axisCapacityLabelFormatter: (Int) -> String = { value -> "$value%" },
    axisTimeLabelFormatter: (Long) -> String = { value -> formatRelativeTime(value) }
) {
    val filteredPoints = normalizePoints(points, recordScreenOffEnabled)
    val rawPoints = points.sortedBy { it.timestamp }
    val powerAxisConfig = remember(filteredPoints, useFixedPowerAxisSegments, fixedPowerAxisMode) {
        if (!useFixedPowerAxisSegments) {
            null
        } else {
            val maxObservedAbsW = when (fixedPowerAxisMode) {
                FixedPowerAxisMode.PositiveOnly -> filteredPoints.maxOfOrNull { it.power } ?: 0.0
                FixedPowerAxisMode.NegativeOnly -> kotlin.math.abs(filteredPoints.minOfOrNull { it.power } ?: 0.0)
            }
            computeFixedPowerAxisConfig(maxObservedAbsW, fixedPowerAxisMode)
        }
    }
    val capacityMarkers = remember(filteredPoints, showCapacityMarkers) {
        if (!showCapacityMarkers) {
            emptyList()
        } else {
            computeCapacityMarkers(filteredPoints)
        }
    }
    val selectedPointState = remember { mutableStateOf<ChartPoint?>(null) }
    val peakLabelText = remember(filteredPoints, showPeakPowerLine, fixedPowerAxisMode, powerLabelFormatter) {
        if (!showPeakPowerLine) {
            null
        } else {
            val peakPlotPowerW = when (fixedPowerAxisMode) {
                FixedPowerAxisMode.NegativeOnly -> filteredPoints.maxOfOrNull { (-it.power).coerceAtLeast(0.0) }
                FixedPowerAxisMode.PositiveOnly -> filteredPoints.maxOfOrNull { it.power }
            } ?: return@remember null

            val displayPowerW = if (fixedPowerAxisMode == FixedPowerAxisMode.NegativeOnly) {
                -peakPlotPowerW
            } else {
                peakPlotPowerW
            }
            powerLabelFormatter(displayPowerW)
        }
    }
    val paddingRightDp = if (peakLabelText == null) {
        32.dp
    } else {
        val density = LocalDensity.current
        val labelPaint = android.graphics.Paint().apply {
            textSize = 24f
            isAntiAlias = true
        }
        with(density) {
            val reservedPx = labelPaint.measureText(peakLabelText) + 8.dp.toPx()
            reservedPx.toDp().coerceAtLeast(32.dp)
        }
    }

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
                            val paddingRight = paddingRightDp.toPx()
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
                            val paddingRight = paddingRightDp.toPx()
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
                val paddingRight = paddingRightDp.toPx()
                val paddingTop = 6.dp.toPx()
                val paddingBottom = 24.dp.toPx()
                val chartWidth = size.width - paddingLeft - paddingRight
                val chartHeight = size.height - paddingTop - paddingBottom
                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                val minTime = filteredPoints.minOf { it.timestamp }
                val maxTime = filteredPoints.maxOf { it.timestamp }
                val minPower = powerAxisConfig?.minValue ?: filteredPoints.minOf { it.power }
                val maxPower = powerAxisConfig?.maxValue ?: filteredPoints.maxOf { it.power }
                val minCapacity = 0.0
                val maxCapacity = 100.0

                val powerValueSelector: (ChartPoint) -> Double = when {
                    powerAxisConfig == null -> {
                        { it.power }
                    }
                    fixedPowerAxisMode == FixedPowerAxisMode.NegativeOnly -> {
                        { (-it.power).coerceIn(minPower, maxPower) }
                    }
                    else -> {
                        { it.power.coerceIn(minPower, maxPower) }
                    }
                }
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
                    valueSelector = powerValueSelector
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

                if (powerAxisConfig == null) {
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
                        showCapacityAxis = showCapacityAxis,
                        powerLabelFormatter = axisPowerLabelFormatter,
                        capacityLabelFormatter = axisCapacityLabelFormatter,
                        timeLabelFormatter = axisTimeLabelFormatter
                    )
                } else {
                    drawVerticalGridLines(
                        paddingLeft = paddingLeft,
                        paddingTop = paddingTop,
                        chartWidth = chartWidth,
                        chartHeight = chartHeight,
                        gridColor = gridColor
                    )
                    drawFixedPowerGridLines(
                        paddingLeft = paddingLeft,
                        paddingTop = paddingTop,
                        chartWidth = chartWidth,
                        chartHeight = chartHeight,
                        minPower = minPower,
                        maxPower = maxPower,
                        gridColor = gridColor,
                        majorStepW = powerAxisConfig.majorStepW,
                        minorStepW = powerAxisConfig.minorStepW
                    )
                    drawFixedPowerAxisLabels(
                        paddingLeft = paddingLeft,
                        paddingTop = paddingTop,
                        chartWidth = chartWidth,
                        chartHeight = chartHeight,
                        minPower = minPower,
                        maxPower = maxPower,
                        gridColor = gridColor,
                        majorStepW = powerAxisConfig.majorStepW,
                        minorStepW = powerAxisConfig.minorStepW,
                        labelSignMultiplier = if (fixedPowerAxisMode == FixedPowerAxisMode.NegativeOnly) -1 else 1,
                        powerLabelFormatter = axisPowerLabelFormatter
                    )
                    drawTimeAxisLabels(
                        paddingLeft = paddingLeft,
                        paddingTop = paddingTop,
                        chartWidth = chartWidth,
                        chartHeight = chartHeight,
                        minTime = minTime,
                        maxTime = maxTime,
                        gridColor = gridColor,
                        timeLabelFormatter = axisTimeLabelFormatter
                    )
                }

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

                if (showPeakPowerLine) {
                    val peakPlotPowerW = filteredPoints.maxOfOrNull { powerValueSelector(it) }
                    if (peakPlotPowerW != null) {
                        val peakY = mapToY(
                            value = peakPlotPowerW,
                            minValue = minPower,
                            maxValue = maxPower,
                            paddingTop = paddingTop,
                            chartHeight = chartHeight
                        )
                        drawLine(
                            color = peakLineColor.copy(alpha = 0.9f),
                            start = Offset(paddingLeft, peakY),
                            end = Offset(paddingLeft + chartWidth, peakY),
                            strokeWidth = 1.dp.toPx()
                        )

                        val displayPowerW = if (fixedPowerAxisMode == FixedPowerAxisMode.NegativeOnly) {
                            -peakPlotPowerW
                        } else {
                            peakPlotPowerW
                        }
                        val label = powerLabelFormatter(displayPowerW)
                        val labelPaint = android.graphics.Paint().apply {
                            color = peakLineColor.toArgb()
                            textSize = 24f
                            isAntiAlias = true
                        }

                        val plotRight = paddingLeft + chartWidth
                        val labelWidth = labelPaint.measureText(label)
                        val preferredX = plotRight + 4.dp.toPx()
                        val maxX = size.width - labelWidth - 4.dp.toPx()
                        val labelX = preferredX.coerceAtMost(maxX).coerceAtLeast(plotRight + 2.dp.toPx())
                        val labelY = (peakY - 4.dp.toPx())
                            .coerceIn(paddingTop + 12.dp.toPx(), paddingTop + chartHeight - 4.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, labelPaint)
                    }
                }

                if (showCapacityMarkers && capacityMarkers.isNotEmpty()) {
                    drawCapacityMarkers(
                        markers = capacityMarkers,
                        minTime = minTime,
                        maxTime = maxTime,
                        paddingLeft = paddingLeft,
                        paddingTop = paddingTop,
                        chartWidth = chartWidth,
                        chartHeight = chartHeight * 0.9f,
                        capacityColor = capacityColor
                    )
                }

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
                        value = powerValueSelector(selectedPoint),
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVerticalGridLines(
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    gridColor: Color
) {
    val cols = 4
    val colStep = chartWidth / cols
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFixedPowerGridLines(
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    minPower: Double,
    maxPower: Double,
    gridColor: Color,
    majorStepW: Int,
    minorStepW: Int
) {
    val minW = minPower.roundToInt()
    val maxW = maxPower.roundToInt()
    val minor = minorStepW.coerceAtLeast(1)
    val major = majorStepW.coerceAtLeast(minor)

    val dashIntervals = floatArrayOf(6.dp.toPx(), 4.dp.toPx())
    val dashEffect = PathEffect.dashPathEffect(dashIntervals, 0f)

    var value = minW
    while (value <= maxW) {
        val isMajor = value % major == 0
        val alpha = if (isMajor) 0.35f else 0.18f
        val stroke = if (isMajor) 1.dp.toPx() else 0.8.dp.toPx()
        val y = mapToY(
            value = value.toDouble(),
            minValue = minPower,
            maxValue = maxPower,
            paddingTop = paddingTop,
            chartHeight = chartHeight
        )
        drawLine(
            color = gridColor.copy(alpha = alpha),
            start = Offset(paddingLeft, y),
            end = Offset(paddingLeft + chartWidth, y),
            strokeWidth = stroke,
            pathEffect = if (isMajor) null else dashEffect
        )
        value += minor
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFixedPowerAxisLabels(
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    minPower: Double,
    maxPower: Double,
    gridColor: Color,
    majorStepW: Int,
    minorStepW: Int,
    labelSignMultiplier: Int,
    powerLabelFormatter: (Double) -> String,
) {
    val textPaint = android.graphics.Paint().apply {
        color = gridColor.toArgb()
        textSize = 24f
        isAntiAlias = true
    }

    val minW = minPower.roundToInt()
    val maxW = maxPower.roundToInt()
    val minor = minorStepW.coerceAtLeast(1)
    val major = majorStepW.coerceAtLeast(minor)

    var value = minW
    while (value <= maxW) {
        if (value % major == 0) {
            val y = mapToY(
                value = value.toDouble(),
                minValue = minPower,
                maxValue = maxPower,
                paddingTop = paddingTop,
                chartHeight = chartHeight
            )
            val powerText = powerLabelFormatter((value * labelSignMultiplier).toDouble())
            val powerWidth = textPaint.measureText(powerText)
            drawContext.canvas.nativeCanvas.drawText(
                powerText,
                paddingLeft - powerWidth - 8.dp.toPx(),
                y - 4.dp.toPx(),
                textPaint
            )
        }
        value += minor
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeAxisLabels(
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    minTime: Long,
    maxTime: Long,
    gridColor: Color,
    timeLabelFormatter: (Long) -> String
) {
    val textPaint = android.graphics.Paint().apply {
        color = gridColor.toArgb()
        textSize = 24f
        isAntiAlias = true
    }
    val cols = 3
    val colStep = chartWidth / cols
    val timeRange = max(1L, maxTime - minTime).toDouble()

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
    showCapacityAxis: Boolean,
    powerLabelFormatter: (Double) -> String,
    capacityLabelFormatter: (Int) -> String,
    timeLabelFormatter: (Long) -> String
) {
    val textPaint = android.graphics.Paint().apply {
        color = gridColor.toArgb()
        textSize = 24f
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
        if (showCapacityAxis) {
            val capacityText = capacityLabelFormatter(capacityValue.roundToInt())
            drawContext.canvas.nativeCanvas.drawText(
                capacityText,
                paddingLeft + chartWidth + 8.dp.toPx(),
                y - 4.dp.toPx(),
                textPaint
            )
        }
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

private data class FixedPowerAxisConfig(
    val minValue: Double,
    val maxValue: Double,
    val majorStepW: Int,
    val minorStepW: Int,
)

enum class FixedPowerAxisMode {
    PositiveOnly,
    NegativeOnly,
}

private fun computeFixedPowerAxisConfig(
    maxObservedAbsW: Double,
    mode: FixedPowerAxisMode
): FixedPowerAxisConfig {
    val axisMaxW = when {
        maxObservedAbsW > 200 -> 240
        maxObservedAbsW >= 150 -> 210
        maxObservedAbsW >= 120 -> 150
        maxObservedAbsW >= 100 -> 120
        maxObservedAbsW >= 80 -> 100
        maxObservedAbsW >= 60 -> 80
        maxObservedAbsW >= 45 -> 60
        maxObservedAbsW >= 30 -> 45
        maxObservedAbsW > 15 -> 30
        maxObservedAbsW > 10 -> 15
        else -> 10
    }

    val majorStepW = when (axisMaxW) {
        10 -> 5
        15 -> 5
        30 -> 10
        45 -> 15
        60, 80, 100, 120 -> 20
        150, 210, 240 -> 30
        else -> 20
    }

    val minorStepW = when {
        axisMaxW <= 15 -> 1
        axisMaxW <= 60 -> 5
        else -> 10
    }

    val minValue = 0.0
    val maxValue = axisMaxW.toDouble()

    return FixedPowerAxisConfig(
        minValue = minValue,
        maxValue = maxValue,
        majorStepW = majorStepW,
        minorStepW = minorStepW
    )
}

private data class CapacityMarker(
    val timestamp: Long,
    val capacity: Int,
    val label: String,
)

private fun computeCapacityMarkers(points: List<ChartPoint>): List<CapacityMarker> {
    if (points.isEmpty()) return emptyList()
    val sorted = points.sortedBy { it.timestamp }
    val startCapacity = sorted.first().capacity
    val endCapacity = sorted.last().capacity
    val delta = kotlin.math.abs(endCapacity - startCapacity)
    val step = if (delta <= 30) 5 else 10

    val minCap = minOf(startCapacity, endCapacity)
    val maxCap = maxOf(startCapacity, endCapacity)

    val targets = LinkedHashSet<Int>()
    targets.add(startCapacity)
    targets.add(endCapacity)

    val firstMultiple = ((minCap + step - 1) / step) * step
    val lastMultiple = (maxCap / step) * step
    var value = firstMultiple
    while (value <= lastMultiple) {
        targets.add(value)
        value += step
    }

    val usedTimestamps = HashSet<Long>()
    val markers = ArrayList<CapacityMarker>(targets.size)
    for (target in targets.toList().sorted()) {
        val nearest = sorted.minByOrNull { kotlin.math.abs(it.capacity - target) } ?: continue
        if (!usedTimestamps.add(nearest.timestamp)) continue
        markers.add(
            CapacityMarker(
                timestamp = nearest.timestamp,
                capacity = nearest.capacity,
                label = "$target%"
            )
        )
    }
    return markers.sortedBy { it.timestamp }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCapacityMarkers(
    markers: List<CapacityMarker>,
    minTime: Long,
    maxTime: Long,
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    capacityColor: Color
) {
    val textPaint = android.graphics.Paint().apply {
        color = capacityColor.toArgb()
        textSize = 20f
        isAntiAlias = true
    }

    val timeRange = max(1L, maxTime - minTime).toDouble()
    val padding = 6.dp.toPx()
    val fontMetrics = textPaint.fontMetrics
    val textHeight = -fontMetrics.ascent
    val chartRight = paddingLeft + chartWidth
    val chartBottom = paddingTop + chartHeight

    markers.forEach { marker ->
        val x = paddingLeft + ((marker.timestamp - minTime) / timeRange).toFloat() * chartWidth
        val y = mapToY(
            value = marker.capacity.toDouble(),
            minValue = 0.0,
            maxValue = 100.0,
            paddingTop = paddingTop,
            chartHeight = chartHeight
        )

        drawCircle(
            color = capacityColor,
            radius = 3.dp.toPx() * 0.65f,
            center = Offset(x, y)
        )

        val labelWidth = textPaint.measureText(marker.label)

        var textX = x + padding
        if (textX + labelWidth > chartRight) {
            textX = x - padding - labelWidth
        }
        if (textX < paddingLeft) {
            textX = paddingLeft
        }

        var textY = y - padding
        if (textY - textHeight < paddingTop) {
            textY = y + textHeight + padding
        }
        if (textY > chartBottom) {
            textY = chartBottom
        }

        drawContext.canvas.nativeCanvas.drawText(
            marker.label,
            textX,
            textY,
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
