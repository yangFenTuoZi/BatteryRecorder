package yangfentuozi.batteryrecorder.ui.components.charts

import android.graphics.Paint
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 创建标准文本画笔
 */
private fun createTextPaint(color: Int, textSize: Float = 24f) = Paint().apply {
    this.color = color
    this.textSize = textSize
    isAntiAlias = true
}

/**
 * 图表坐标系统，封装坐标转换逻辑
 */
private class ChartCoordinates(
    val paddingLeft: Float,
    val paddingTop: Float,
    val chartWidth: Float,
    val chartHeight: Float,
    val minTime: Long,
    val maxTime: Long,
    val minPower: Double,
    val maxPower: Double,
) {
    val timeRange = max(1L, maxTime - minTime).toDouble()
    private val powerRange = max(1e-6, maxPower - minPower)

    fun timeToX(timestamp: Long): Float =
        paddingLeft + ((timestamp - minTime) / timeRange).toFloat() * chartWidth

    fun powerToY(value: Double): Float {
        val normalized = ((value - minPower) / powerRange).toFloat()
        return paddingTop + (1f - normalized) * chartHeight
    }

    fun capacityToY(capacity: Double, scale: Float = 0.9f): Float {
        val normalized = (capacity / 100.0).toFloat()
        return paddingTop + (1f - normalized) * chartHeight * scale
    }

    /**
     * 根据 X 坐标查找最近的数据点
     */
    fun findPointAtX(offsetX: Float, points: List<ChartPoint>): ChartPoint? {
        if (chartWidth <= 0f) return null
        val x = (offsetX - paddingLeft).coerceIn(0f, chartWidth)
        val targetTime = minTime + (x / chartWidth * timeRange).toLong()
        return points.minByOrNull { abs(it.timestamp - targetTime) }
    }
}

/**
 * 功率-电量双轴图表，支持交互选点、峰值线、屏幕状态线等功能
 *
 * @param points 数据点列表
 * @param recordScreenOffEnabled 是否记录息屏数据
 * @param modifier 修饰符
 * @param powerColor 功率曲线颜色
 * @param capacityColor 电量曲线颜色
 * @param gridColor 网格线颜色
 * @param strokeWidth 曲线宽度
 * @param screenOnColor 亮屏状态线颜色
 * @param screenOffColor 息屏状态线颜色
 * @param peakLineColor 峰值线颜色
 * @param showPeakPowerLine 是否显示峰值线
 * @param showScreenStateLine 是否显示屏幕状态线
 * @param useFixedPowerAxisSegments 是否使用固定功率轴刻度
 * @param fixedPowerAxisMode 固定功率轴模式
 * @param showCapacityAxis 是否显示电量轴
 * @param showCapacityMarkers 是否显示电量标记点
 * @param powerLabelFormatter 功率标签格式化器
 * @param capacityLabelFormatter 电量标签格式化器
 * @param timeLabelFormatter 时间标签格式化器
 * @param axisPowerLabelFormatter 功率轴标签格式化器
 * @param axisCapacityLabelFormatter 电量轴标签格式化器
 * @param axisTimeLabelFormatter 时间轴标签格式化器
 */
@Composable
fun PowerCapacityChart(
    points: List<ChartPoint>,
    recordScreenOffEnabled: Boolean,
    modifier: Modifier = Modifier,
    powerColor: Color = MaterialTheme.colorScheme.primary,
    capacityColor: Color = Color(0xFFFFB300),
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
    powerLabelFormatter: (Double) -> String = { value -> String.format("%.2f", value) },
    capacityLabelFormatter: (Int) -> String = { value -> "$value%" },
    timeLabelFormatter: (Long) -> String = { value -> value.toString() },
    axisPowerLabelFormatter: (Double) -> String = { value -> value.roundToInt().toString() },
    axisCapacityLabelFormatter: (Int) -> String = { value -> "$value%" },
    axisTimeLabelFormatter: (Long) -> String = { value -> formatRelativeTime(value) }
) {
    val filteredPoints = normalizePoints(points, recordScreenOffEnabled)
    val rawPoints = points.sortedBy { it.timestamp }

    // 计算固定功率轴配置（根据最大功率值自动选择刻度范围）
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
    val isNegativeMode = fixedPowerAxisMode == FixedPowerAxisMode.NegativeOnly

    // 预计算峰值标签文本，用于动态调整右侧 padding
    val peakLabelText = remember(filteredPoints, showPeakPowerLine, isNegativeMode, powerLabelFormatter) {
        if (!showPeakPowerLine) return@remember null
        val peakPlotPowerW = filteredPoints.maxOfOrNull {
            if (isNegativeMode) (-it.power).coerceAtLeast(0.0) else it.power
        } ?: return@remember null
        powerLabelFormatter(if (isNegativeMode) -peakPlotPowerW else peakPlotPowerW)
    }

    val density = LocalDensity.current
    // 根据峰值标签宽度动态计算右侧 padding
    val paddingRightDp = remember(peakLabelText) {
        if (peakLabelText == null) 32.dp
        else with(density) {
            val reservedPx = createTextPaint(0).measureText(peakLabelText) + 8.dp.toPx()
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
                    .pointerInput(filteredPoints, paddingRightDp) {
                        val paddingLeft = 32.dp.toPx()
                        val chartWidth = size.width - paddingLeft - paddingRightDp.toPx()
                        val minTime = filteredPoints.minOf { it.timestamp }
                        val maxTime = filteredPoints.maxOf { it.timestamp }
                        val coords = ChartCoordinates(paddingLeft, 0f, chartWidth, 0f, minTime, maxTime, 0.0, 0.0)
                        detectTapGestures { offset ->
                            selectedPointState.value = coords.findPointAtX(offset.x, filteredPoints)
                        }
                    }
                    .pointerInput(filteredPoints, paddingRightDp) {
                        val paddingLeft = 32.dp.toPx()
                        val chartWidth = size.width - paddingLeft - paddingRightDp.toPx()
                        val minTime = filteredPoints.minOf { it.timestamp }
                        val maxTime = filteredPoints.maxOf { it.timestamp }
                        val coords = ChartCoordinates(paddingLeft, 0f, chartWidth, 0f, minTime, maxTime, 0.0, 0.0)
                        detectDragGestures { change, _ ->
                            change.consume()
                            selectedPointState.value = coords.findPointAtX(change.position.x, filteredPoints)
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

                val coords = ChartCoordinates(
                    paddingLeft, paddingTop, chartWidth, chartHeight,
                    minTime, maxTime, minPower, maxPower
                )

                // 根据模式选择功率值转换策略
                val powerValueSelector: (ChartPoint) -> Double = when {
                    powerAxisConfig == null -> { p -> p.power }
                    isNegativeMode -> { p -> (-p.power).coerceIn(minPower, maxPower) }
                    else -> { p -> p.power.coerceIn(minPower, maxPower) }
                }
                val powerPath = buildPath(filteredPoints, coords, powerValueSelector)
                val capacityPath = buildCapacityPath(filteredPoints, coords) { it.capacity.toDouble() }

                // 绘制网格和坐标轴（根据是否使用固定轴选择不同绘制策略）
                if (powerAxisConfig == null) {
                    drawGrid(coords, gridColor)
                    drawAxisLabels(
                        coords, paddingRight, gridColor, showCapacityAxis,
                        axisPowerLabelFormatter, axisCapacityLabelFormatter, axisTimeLabelFormatter
                    )
                } else {
                    drawVerticalGridLines(coords, gridColor)
                    drawFixedPowerGridLines(coords, gridColor, powerAxisConfig.majorStepW, powerAxisConfig.minorStepW)
                    drawFixedPowerAxisLabels(
                        coords, gridColor, powerAxisConfig.majorStepW, powerAxisConfig.minorStepW,
                        if (isNegativeMode) -1 else 1, axisPowerLabelFormatter
                    )
                    drawTimeAxisLabels(coords, gridColor, axisTimeLabelFormatter)
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

                // 滑动选择器
                if (showPeakPowerLine) {
                    val peakPlotPowerW = filteredPoints.maxOfOrNull { powerValueSelector(it) }
                    if (peakPlotPowerW != null) {
                        val peakY = coords.powerToY(peakPlotPowerW)
                        drawLine(
                            color = peakLineColor.copy(alpha = 0.9f),
                            start = Offset(paddingLeft, peakY),
                            end = Offset(paddingLeft + chartWidth, peakY),
                            strokeWidth = 1.dp.toPx()
                        )

                        val label = powerLabelFormatter(if (isNegativeMode) -peakPlotPowerW else peakPlotPowerW)
                        val labelPaint = createTextPaint(peakLineColor.toArgb())
                        val labelWidth = labelPaint.measureText(label)
                        val plotRight = paddingLeft + chartWidth
                        val labelX = (plotRight + 4.dp.toPx())
                            .coerceAtMost(size.width - labelWidth - 4.dp.toPx())
                            .coerceAtLeast(plotRight + 2.dp.toPx())
                        val labelY = (peakY - 4.dp.toPx())
                            .coerceIn(paddingTop + 12.dp.toPx(), paddingTop + chartHeight - 4.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, labelPaint)
                    }
                }

                if (showCapacityMarkers && capacityMarkers.isNotEmpty()) {
                    drawCapacityMarkers(capacityMarkers, coords, capacityColor)
                }

                if (showScreenStateLine && rawPoints.isNotEmpty()) {
                    drawScreenStateLine(rawPoints, coords, screenOnColor, screenOffColor, 4.dp)
                }

                selectedPointState.value?.let { selectedPoint ->
                    val selectedX = coords.timeToX(selectedPoint.timestamp)
                    val powerY = coords.powerToY(powerValueSelector(selectedPoint))
                    val capacityY = coords.capacityToY(selectedPoint.capacity.toDouble())

                    drawLine(
                        color = gridColor.copy(alpha = 0.6f),
                        start = Offset(selectedX, paddingTop),
                        end = Offset(selectedX, paddingTop + chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(powerColor, radius = 2.8.dp.toPx(), center = Offset(selectedX, powerY))
                    drawCircle(capacityColor, radius = 2.8.dp.toPx(), center = Offset(selectedX, capacityY))
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
                LegendItem(label = "亮屏", color = screenOnColor)
                LegendItem(label = "息屏", color = screenOffColor)
            }
        }
    }
}

/**
 * 显示选中数据点的详细信息
 */
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

/**
 * 图例项：圆点 + 标签
 */
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

/**
 * 过滤息屏期间的孤立数据点（前后均为亮屏时跳过）
 */
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
        // 跳过前后均为亮屏的孤立息屏点
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

/**
 * 构建功率曲线路径
 */
private fun buildPath(
    points: List<ChartPoint>,
    coords: ChartCoordinates,
    valueSelector: (ChartPoint) -> Double
): Path {
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = coords.timeToX(point.timestamp)
        val y = coords.powerToY(valueSelector(point))
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

/**
 * 构建电量曲线路径（固定 0.9 缩放，避免与底部时间轴重叠）
 */
private fun buildCapacityPath(
    points: List<ChartPoint>,
    coords: ChartCoordinates,
    valueSelector: (ChartPoint) -> Double
): Path {
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = coords.timeToX(point.timestamp)
        val y = coords.capacityToY(valueSelector(point), 0.9f)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

/**
 * 绘制网格线（水平+垂直）
 */
private fun DrawScope.drawGrid(coords: ChartCoordinates, gridColor: Color) {
    val rows = 4
    val cols = 4
    val rowStep = coords.chartHeight / rows
    val colStep = coords.chartWidth / cols
    val lineColor = gridColor.copy(alpha = 0.3f)
    val stroke = 1.dp.toPx()

    for (i in 0..rows) {
        val y = coords.paddingTop + rowStep * i
        drawLine(lineColor, Offset(coords.paddingLeft, y), Offset(coords.paddingLeft + coords.chartWidth, y), stroke)
    }
    for (i in 0..cols) {
        val x = coords.paddingLeft + colStep * i
        drawLine(lineColor, Offset(x, coords.paddingTop), Offset(x, coords.paddingTop + coords.chartHeight), stroke)
    }
}

/**
 * 仅绘制垂直网格线
 */
private fun DrawScope.drawVerticalGridLines(coords: ChartCoordinates, gridColor: Color) {
    val cols = 4
    val colStep = coords.chartWidth / cols
    val lineColor = gridColor.copy(alpha = 0.3f)
    val stroke = 1.dp.toPx()

    for (i in 0..cols) {
        val x = coords.paddingLeft + colStep * i
        drawLine(lineColor, Offset(x, coords.paddingTop), Offset(x, coords.paddingTop + coords.chartHeight), stroke)
    }
}

/**
 * 绘制固定功率轴的水平网格线（主刻度实线，次刻度虚线）
 */
private fun DrawScope.drawFixedPowerGridLines(
    coords: ChartCoordinates,
    gridColor: Color,
    majorStepW: Int,
    minorStepW: Int
) {
    val minW = coords.minPower.roundToInt()
    val maxW = coords.maxPower.roundToInt()
    val minor = minorStepW.coerceAtLeast(1)
    val major = majorStepW.coerceAtLeast(minor)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)

    // 绘制主刻度（实线）和次刻度（虚线）
    var value = minW
    while (value <= maxW) {
        val isMajor = value % major == 0
        val y = coords.powerToY(value.toDouble())
        drawLine(
            color = gridColor.copy(alpha = if (isMajor) 0.35f else 0.18f),
            start = Offset(coords.paddingLeft, y),
            end = Offset(coords.paddingLeft + coords.chartWidth, y),
            strokeWidth = if (isMajor) 1.dp.toPx() else 0.8.dp.toPx(),
            pathEffect = if (isMajor) null else dashEffect
        )
        value += minor
    }
}

/**
 * 绘制固定功率轴的刻度标签
 */
private fun DrawScope.drawFixedPowerAxisLabels(
    coords: ChartCoordinates,
    gridColor: Color,
    majorStepW: Int,
    minorStepW: Int,
    labelSignMultiplier: Int,
    powerLabelFormatter: (Double) -> String
) {
    val textPaint = createTextPaint(gridColor.toArgb())
    val minW = coords.minPower.roundToInt()
    val maxW = coords.maxPower.roundToInt()
    val minor = minorStepW.coerceAtLeast(1)
    val major = majorStepW.coerceAtLeast(minor)

    // 仅绘制主刻度标签
    var value = minW
    while (value <= maxW) {
        if (value % major == 0) {
            val y = coords.powerToY(value.toDouble())
            val powerText = powerLabelFormatter((value * labelSignMultiplier).toDouble())
            val powerWidth = textPaint.measureText(powerText)
            drawContext.canvas.nativeCanvas.drawText(
                powerText,
                coords.paddingLeft - powerWidth - 8.dp.toPx(),
                y - 4.dp.toPx(),
                textPaint
            )
        }
        value += minor
    }
}

/**
 * 绘制时间轴刻度标签
 */
private fun DrawScope.drawTimeAxisLabels(
    coords: ChartCoordinates,
    gridColor: Color,
    timeLabelFormatter: (Long) -> String
) {
    val textPaint = createTextPaint(gridColor.toArgb())
    val cols = 3
    val colStep = coords.chartWidth / cols

    for (i in 0..cols) {
        val x = coords.paddingLeft + colStep * i
        val timeValue = (coords.timeRange * i / cols).toLong()
        val text = timeLabelFormatter(timeValue)
        val textWidth = textPaint.measureText(text)
        drawContext.canvas.nativeCanvas.drawText(
            text,
            x - textWidth / 2f,
            coords.paddingTop + coords.chartHeight + 24.dp.toPx(),
            textPaint
        )
    }
}

/**
 * 绘制屏幕状态线（亮屏/息屏分色显示）
 */
private fun DrawScope.drawScreenStateLine(
    points: List<ChartPoint>,
    coords: ChartCoordinates,
    screenOnColor: Color,
    screenOffColor: Color,
    strokeWidth: Dp
) {
    if (points.isEmpty()) return
    val y = coords.paddingTop + coords.chartHeight + 8.dp.toPx()

    if (points.size == 1) {
        drawLine(
            color = if (points[0].isDisplayOn) screenOnColor else screenOffColor,
            start = Offset(coords.paddingLeft, y),
            end = Offset(coords.paddingLeft + coords.chartWidth, y),
            strokeWidth = strokeWidth.toPx()
        )
        return
    }

    val screenOnPath = Path()
    val screenOffPath = Path()
    var lastOnX = Float.NaN
    var lastOffX = Float.NaN

    for (i in 0 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        val startX = coords.timeToX(current.timestamp)
        val endX = coords.timeToX(next.timestamp)

        if (current.isDisplayOn) {
            if (startX != lastOnX) screenOnPath.moveTo(startX, y)
            screenOnPath.lineTo(endX, y)
            lastOnX = endX
        } else {
            if (startX != lastOffX) screenOffPath.moveTo(startX, y)
            screenOffPath.lineTo(endX, y)
            lastOffX = endX
        }
    }

    val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
    if (!lastOnX.isNaN()) drawPath(screenOnPath, screenOnColor, style = stroke)
    if (!lastOffX.isNaN()) drawPath(screenOffPath, screenOffColor, style = stroke)
}

/**
 * 绘制动态轴刻度标签（功率、电量、时间）
 */
private fun DrawScope.drawAxisLabels(
    coords: ChartCoordinates,
    paddingRight: Float,
    gridColor: Color,
    showCapacityAxis: Boolean,
    powerLabelFormatter: (Double) -> String,
    capacityLabelFormatter: (Int) -> String,
    timeLabelFormatter: (Long) -> String
) {
    val textPaint = createTextPaint(gridColor.toArgb())
    val rows = 4
    val cols = 3
    val rowStep = coords.chartHeight / rows
    val colStep = coords.chartWidth / cols
    val powerRange = max(1e-6, coords.maxPower - coords.minPower)

    for (i in 0..rows) {
        val y = coords.paddingTop + rowStep * i
        val powerValue = coords.maxPower - (powerRange * i / rows)
        val capacityValue = 100 - (100 * i / rows)
        val powerText = powerLabelFormatter(powerValue)
        val powerWidth = textPaint.measureText(powerText)
        drawContext.canvas.nativeCanvas.drawText(
            powerText,
            coords.paddingLeft - powerWidth - 8.dp.toPx(),
            y - 4.dp.toPx(),
            textPaint
        )
        if (showCapacityAxis) {
            val capacityText = capacityLabelFormatter(capacityValue)
            drawContext.canvas.nativeCanvas.drawText(
                capacityText,
                coords.paddingLeft + coords.chartWidth + 8.dp.toPx(),
                y - 4.dp.toPx(),
                textPaint
            )
        }
    }

    for (i in 0..cols) {
        val x = coords.paddingLeft + colStep * i
        val timeValue = (coords.timeRange * i / cols).toLong()
        val text = timeLabelFormatter(timeValue)
        val textWidth = textPaint.measureText(text)
        drawContext.canvas.nativeCanvas.drawText(
            text,
            x - textWidth / 2f,
            coords.paddingTop + coords.chartHeight + 24.dp.toPx(),
            textPaint
        )
    }
}

/**
 * 固定功率轴配置
 */
private data class FixedPowerAxisConfig(
    val minValue: Double,
    val maxValue: Double,
    val majorStepW: Int,
    val minorStepW: Int,
)

/**
 * 固定功率轴模式：正值（充电）或负值（放电）
 */
enum class FixedPowerAxisMode {
    PositiveOnly,
    NegativeOnly,
}

/**
 * 根据最大功率值计算固定轴配置（自动选择合适的刻度范围）
 */
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

/**
 * 电量标记点
 */
private data class CapacityMarker(
    val timestamp: Long,
    val capacity: Int,
    val label: String,
)

/**
 * 计算电量标记点（起止点 + 整数倍刻度）
 */
private fun computeCapacityMarkers(points: List<ChartPoint>): List<CapacityMarker> {
    if (points.isEmpty()) return emptyList()
    val sorted = points.sortedBy { it.timestamp }
    val startCapacity = sorted.first().capacity
    val endCapacity = sorted.last().capacity
    val delta = kotlin.math.abs(endCapacity - startCapacity)
    val step = if (delta <= 30) 5 else 10

    val minCap = minOf(startCapacity, endCapacity)
    val maxCap = maxOf(startCapacity, endCapacity)

    // 收集目标电量值：起止点 + 整数倍刻度
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

    // 为每个目标电量值找到最近的数据点，避免重复时间戳
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

/**
 * 绘制电量标记点及标签
 */
private fun DrawScope.drawCapacityMarkers(
    markers: List<CapacityMarker>,
    coords: ChartCoordinates,
    capacityColor: Color
) {
    val textPaint = createTextPaint(capacityColor.toArgb(), 20f)
    val padding = 6.dp.toPx()
    val textHeight = -textPaint.fontMetrics.ascent
    val chartRight = coords.paddingLeft + coords.chartWidth
    val scaledChartHeight = coords.chartHeight * 0.9f
    val chartBottom = coords.paddingTop + scaledChartHeight

    markers.forEach { marker ->
        val x = coords.timeToX(marker.timestamp)
        val y = coords.capacityToY(marker.capacity.toDouble())

        drawCircle(capacityColor, radius = 3.dp.toPx() * 0.65f, center = Offset(x, y))

        // 智能定位标签：优先右侧，超出边界则左侧
        val labelWidth = textPaint.measureText(marker.label)
        var textX = x + padding
        if (textX + labelWidth > chartRight) textX = x - padding - labelWidth
        if (textX < coords.paddingLeft) textX = coords.paddingLeft

        var textY = y - padding
        if (textY - textHeight < coords.paddingTop) textY = y + textHeight + padding
        if (textY > chartBottom) textY = chartBottom

        drawContext.canvas.nativeCanvas.drawText(marker.label, textX, textY, textPaint)
    }
}

/**
 * 格式化相对时间（如 1h30m）
 */
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
