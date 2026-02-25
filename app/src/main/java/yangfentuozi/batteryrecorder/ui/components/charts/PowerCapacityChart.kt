package yangfentuozi.batteryrecorder.ui.components.charts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.utils.formatRelativeTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.Locale

/**
 * 创建标准文本画笔
 */
private fun createTextPaint(color: Int, textSize: Float) = Paint().apply {
    this.color = color
    this.textSize = textSize
    isAntiAlias = true
}

private const val CAPACITY_CURVE_SCALE = 0.9f
private val CAPACITY_COLOR = Color(0xFFFFB300)
private val TEMP_COLOR = Color(0xFFFF8A65)
private val SCREEN_ON_COLOR = Color(0xFF2E7D32)
private val SCREEN_OFF_COLOR = Color(0xFFD32F2F)
private val LINE_STROKE_WIDTH = 1.3.dp
private const val TEMP_EXPAND_STEP_TENTHS = 100.0    // 10℃

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
    val minTemp: Double,
    val maxTemp: Double,
) {
    val timeRange = max(1L, maxTime - minTime).toDouble()
    private val powerRange = max(1e-6, maxPower - minPower)

    fun timeToX(timestamp: Long): Float =
        paddingLeft + ((timestamp - minTime) / timeRange).toFloat() * chartWidth

    fun powerToY(value: Double): Float {
        val normalized = ((value - minPower) / powerRange).toFloat()
        return paddingTop + (1f - normalized) * chartHeight
    }

    fun capacityToY(capacity: Double): Float {
        val normalized = (capacity / 100.0).toFloat()
        return paddingTop + (1f - normalized) * chartHeight * CAPACITY_CURVE_SCALE
    }

    fun tempToY(temp: Double): Float {
        val tempRange = max(1.0, maxTemp - minTemp)
        val normalized = ((temp - minTemp) / tempRange).toFloat()
        return paddingTop + (1f - normalized) * chartHeight * 0.9f
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

/** 功率-电量图表（当前仅服务记录详情页单一场景）。 */
@Composable
fun PowerCapacityChart(
    points: List<ChartPoint>,
    recordScreenOffEnabled: Boolean,
    recordStartTime: Long,
    modifier: Modifier,
    fixedPowerAxisMode: FixedPowerAxisMode,
    chartHeight: Dp,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    useFivePercentTimeGrid: Boolean,
    visibleStartTime: Long?,
    visibleEndTime: Long?,
    onViewportShift: ((Long) -> Unit)?,
) {
    val powerColor = MaterialTheme.colorScheme.primary
    val capacityColor = CAPACITY_COLOR
    val tempColor = TEMP_COLOR
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val strokeWidth = LINE_STROKE_WIDTH
    val screenOnColor = SCREEN_ON_COLOR
    val screenOffColor = SCREEN_OFF_COLOR
    val peakLineColor = MaterialTheme.colorScheme.error

    val filteredPoints = normalizePoints(points, recordScreenOffEnabled)
    val rawPoints = points.sortedBy { it.timestamp }
    if (filteredPoints.size < 2) {
        Text(
            text = "暂无数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val fullMinTime = filteredPoints.minOf { it.timestamp }
    val fullMaxTime = filteredPoints.maxOf { it.timestamp }
    val viewportStart = (visibleStartTime ?: fullMinTime).coerceIn(fullMinTime, fullMaxTime)
    val viewportEnd = (visibleEndTime ?: fullMaxTime).coerceIn(viewportStart, fullMaxTime)
    val viewportDurationMs = (viewportEnd - viewportStart).coerceAtLeast(1L)
    val visibleFilteredPoints = remember(filteredPoints, viewportStart, viewportEnd) {
        slicePointsForViewport(filteredPoints, viewportStart, viewportEnd)
    }
    val visibleRawPoints = remember(rawPoints, viewportStart, viewportEnd) {
        slicePointsForViewport(rawPoints, viewportStart, viewportEnd)
    }
    val renderFilteredPoints = if (visibleFilteredPoints.size >= 2) {
        visibleFilteredPoints
    } else {
        filteredPoints
    }
    val renderRawPoints = if (visibleRawPoints.size >= 2) {
        visibleRawPoints
    } else {
        rawPoints
    }
    val selectablePoints = remember(renderFilteredPoints, viewportStart, viewportEnd) {
        renderFilteredPoints.filter { it.timestamp in viewportStart..viewportEnd }
            .ifEmpty { renderFilteredPoints }
    }

    // 计算固定功率轴配置（根据最大功率值自动选择刻度范围）
    val powerAxisConfig = remember(filteredPoints, fixedPowerAxisMode) {
        val maxObservedAbsW = when (fixedPowerAxisMode) {
            FixedPowerAxisMode.PositiveOnly -> filteredPoints.maxOfOrNull { it.power } ?: 0.0
            FixedPowerAxisMode.NegativeOnly -> kotlin.math.abs(filteredPoints.minOfOrNull { it.power } ?: 0.0)
        }
        computeFixedPowerAxisConfig(maxObservedAbsW, fixedPowerAxisMode)
    }
    val capacityMarkers = remember(renderFilteredPoints) {
        computeCapacityMarkers(renderFilteredPoints)
    }
    val selectedPointState = remember { mutableStateOf<ChartPoint?>(null) }
    val isNegativeMode = fixedPowerAxisMode == FixedPowerAxisMode.NegativeOnly

    LaunchedEffect(selectablePoints, viewportStart, viewportEnd) {
        val selected = selectedPointState.value ?: return@LaunchedEffect
        if (selected.timestamp !in viewportStart..viewportEnd ||
            selectablePoints.none { it.timestamp == selected.timestamp }
        ) {
            selectedPointState.value = null
        }
    }

    // 预计算峰值标签文本，用于动态调整右侧 padding
    val peakLabelText = remember(renderFilteredPoints, isNegativeMode) {
        val peakPlotPowerW = renderFilteredPoints.maxOfOrNull {
            if (isNegativeMode) (-it.power).coerceAtLeast(0.0) else it.power
        } ?: return@remember null
        String.format(Locale.getDefault(), "%.2f W", if (isNegativeMode) -peakPlotPowerW else peakPlotPowerW)
    }

    val density = LocalDensity.current

    // 预计算功率轴标签最左侧位置，用于 SelectedPointInfo 对齐
    val powerAxisStartDp = remember(filteredPoints, powerAxisConfig) {
        with(density) {
            val paddingLeftPx = 32.dp.toPx()
            val gapPx = 8.dp.toPx()
            val textPaint = createTextPaint(0, 24f)
            val minP = powerAxisConfig.minValue
            val maxP = powerAxisConfig.maxValue
            val maxLabelWidth = listOf(minP, maxP)
                .maxOf { textPaint.measureText(String.format(Locale.getDefault(), "%.0f W", it)) }
            (paddingLeftPx - maxLabelWidth - gapPx).coerceAtLeast(0f).toDp()
        }
    }

    // 根据峰值标签宽度动态计算右侧 padding
    val paddingRightDp = remember(peakLabelText) {
        if (peakLabelText == null) 32.dp
        else with(density) {
            val reservedPx = createTextPaint(0, 24f).measureText(peakLabelText) + 8.dp.toPx()
            reservedPx.toDp().coerceAtLeast(32.dp)
        }
    }

    Column(modifier = modifier) {
        SelectedPointInfo(
            selected = selectedPointState.value,
            recordStartTime = recordStartTime,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
            startPadding = powerAxisStartDp
        )

        Spacer(modifier = Modifier.height(13.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .pointerInput(renderFilteredPoints, paddingRightDp, viewportStart, viewportEnd, onViewportShift) {
                        if (onViewportShift == null) return@pointerInput
                        val paddingLeft = 32.dp.toPx()
                        val chartWidth = size.width - paddingLeft - paddingRightDp.toPx()
                        if (chartWidth <= 0f) return@pointerInput
                        awaitEachGesture {
                            var lastCentroidX: Float? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val activeChanges = event.changes.filter { it.pressed }
                                if (activeChanges.isEmpty()) break
                                if (activeChanges.size < 2) {
                                    lastCentroidX = null
                                    continue
                                }
                                val centroidX = activeChanges.map { it.position.x }.average().toFloat()
                                val previousCentroidX = lastCentroidX
                                if (previousCentroidX != null) {
                                    val deltaX = centroidX - previousCentroidX
                                    val deltaMs = ((-deltaX / chartWidth) * viewportDurationMs).toLong()
                                    if (deltaMs != 0L) {
                                        onViewportShift(deltaMs)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                                lastCentroidX = centroidX
                            }
                        }
                    }
                    .pointerInput(selectablePoints, paddingRightDp, viewportStart, viewportEnd) {
                        val paddingLeft = 32.dp.toPx()
                        val chartWidth = size.width - paddingLeft - paddingRightDp.toPx()
                        val coords = ChartCoordinates(
                            paddingLeft, 0f, chartWidth, 0f, viewportStart, viewportEnd, 0.0, 0.0, 0.0, 0.0
                        )
                        detectTapGestures { offset ->
                            selectedPointState.value = coords.findPointAtX(offset.x, selectablePoints)
                        }
                    }
                    .pointerInput(selectablePoints, paddingRightDp, viewportStart, viewportEnd) {
                        val paddingLeft = 32.dp.toPx()
                        val chartWidth = size.width - paddingLeft - paddingRightDp.toPx()
                        val coords = ChartCoordinates(
                            paddingLeft, 0f, chartWidth, 0f, viewportStart, viewportEnd, 0.0, 0.0, 0.0, 0.0
                        )
                        detectDragGestures { change, _ ->
                            change.consume()
                            selectedPointState.value = coords.findPointAtX(change.position.x, selectablePoints)
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

                val minPower = powerAxisConfig.minValue
                val maxPower = powerAxisConfig.maxValue
                val (minTemp, maxTemp) = computeTempAxisRange(renderFilteredPoints)
                val verticalGridSegments = if (useFivePercentTimeGrid) 20 else 4
                val timeLabelSegments = if (useFivePercentTimeGrid) 20 else 3
                val timeLabelStep = if (useFivePercentTimeGrid) 4 else 1

                val coords = ChartCoordinates(
                    paddingLeft, paddingTop, chartWidth, chartHeight,
                    viewportStart, viewportEnd, minPower, maxPower, minTemp, maxTemp
                )

                // 根据模式选择功率值转换策略
                val powerValueSelector: (ChartPoint) -> Double = when {
                    isNegativeMode -> { p -> (-p.power).coerceIn(minPower, maxPower) }
                    else -> { p -> p.power.coerceIn(minPower, maxPower) }
                }
                val powerPath = buildPath(renderFilteredPoints, coords, powerValueSelector)
                val capacityPath = buildCapacityPath(renderFilteredPoints, coords) { it.capacity.toDouble() }
                val tempPath = buildTempPath(renderFilteredPoints, coords) { it.temp.toDouble() }

                // 固定功率轴：垂直网格 + 主次刻度水平线 + 固定刻度标签
                drawVerticalGridLines(coords, gridColor, verticalGridSegments)
                drawFixedPowerGridLines(coords, gridColor, powerAxisConfig.majorStepW, powerAxisConfig.minorStepW)
                drawFixedPowerAxisLabels(
                    coords, gridColor, powerAxisConfig.majorStepW, powerAxisConfig.minorStepW,
                    if (isNegativeMode) -1 else 1
                )
                drawTimeAxisLabels(
                    coords, gridColor, { value ->
                        val offset = (value - recordStartTime).coerceAtLeast(0L)
                        formatRelativeTime(offset)
                    },
                    timeLabelSegments, timeLabelStep
                )

                clipRect(
                    left = paddingLeft,
                    top = paddingTop,
                    right = paddingLeft + chartWidth,
                    bottom = paddingTop + chartHeight
                ) {
                    drawPath(
                        path = tempPath,
                        color = tempColor,
                        style = Stroke(
                            width = strokeWidth.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
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
                }

                // 滑动选择器
                val peakPlotPowerW = renderFilteredPoints.maxOfOrNull { powerValueSelector(it) }
                if (peakPlotPowerW != null) {
                    val peakY = coords.powerToY(peakPlotPowerW)
                    drawLine(
                        color = peakLineColor.copy(alpha = 0.9f),
                        start = Offset(paddingLeft, peakY),
                        end = Offset(paddingLeft + chartWidth, peakY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val label = String.format(
                        Locale.getDefault(),
                        "%.2f W",
                        if (isNegativeMode) -peakPlotPowerW else peakPlotPowerW
                    )
                    val labelPaint = createTextPaint(peakLineColor.toArgb(), 24f)
                    val labelWidth = labelPaint.measureText(label)
                    val plotRight = paddingLeft + chartWidth
                    val labelX = (plotRight + 4.dp.toPx())
                        .coerceAtMost(size.width - labelWidth - 4.dp.toPx())
                        .coerceAtLeast(plotRight + 2.dp.toPx())
                    val labelY = (peakY - 4.dp.toPx())
                        .coerceIn(paddingTop + 12.dp.toPx(), paddingTop + chartHeight - 4.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, labelPaint)
                }

                if (capacityMarkers.isNotEmpty()) {
                    drawCapacityMarkers(capacityMarkers, coords, capacityColor)
                }

                drawTempExtremeMarkers(renderFilteredPoints, coords, tempColor)

                if (renderRawPoints.isNotEmpty()) {
                    // 屏幕状态线保留在底部区域，但仅允许在图表横向范围内绘制
                    clipRect(
                        left = paddingLeft,
                        top = 0f,
                        right = paddingLeft + chartWidth,
                        bottom = size.height
                    ) {
                        drawScreenStateLine(renderRawPoints, coords, screenOnColor, screenOffColor, 4.dp)
                    }
                }

                clipRect(
                    left = paddingLeft,
                    top = paddingTop,
                    right = paddingLeft + chartWidth,
                    bottom = paddingTop + chartHeight
                ) {
                    selectedPointState.value
                        ?.takeIf { it.timestamp in viewportStart..viewportEnd }
                        ?.let { selectedPoint ->
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
                        val tempY = coords.tempToY(selectedPoint.temp.toDouble())
                        drawCircle(tempColor, radius = 2.8.dp.toPx(), center = Offset(selectedX, tempY))
                    }
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
            LegendItem(label = "温度", color = tempColor)
            LegendItem(label = "亮屏", color = screenOnColor)
            LegendItem(label = "息屏", color = screenOffColor)
        }
    }
}

/**
 * 显示选中数据点的详细信息
 */
@Composable
private fun SelectedPointInfo(
    selected: ChartPoint?,
    recordStartTime: Long,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    startPadding: Dp,
) {
    val text = if (selected == null) {
        "时间点详细数据"
    } else {
        val offset = (selected.timestamp - recordStartTime).coerceAtLeast(0L)
        val timeText = formatRelativeTime(offset)
        val powerText = String.format(Locale.getDefault(), "%.2f W", selected.power)
        val capacityText = "${selected.capacity}%"
        val tempText =
            if (selected.temp == 0) "" else " · ${String.format(Locale.getDefault(), "%.1f ℃", selected.temp / 10.0)}"
        "$timeText · $powerText · $capacityText$tempText"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = startPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (isFullscreen) "退出全屏" else "放大图表"
            )
        }
    }
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

private fun slicePointsForViewport(points: List<ChartPoint>, startTime: Long, endTime: Long): List<ChartPoint> {
    if (points.isEmpty()) return emptyList()
    val sorted = points.sortedBy { it.timestamp }
    if (startTime <= sorted.first().timestamp && endTime >= sorted.last().timestamp) return sorted

    val inRange = sorted.filter { it.timestamp in startTime..endTime }
    if (inRange.isEmpty()) {
        val previous = sorted.lastOrNull { it.timestamp < startTime }
        val next = sorted.firstOrNull { it.timestamp > endTime }
        return listOfNotNull(previous, next)
    }

    val previous = sorted.lastOrNull { it.timestamp < startTime }
    val next = sorted.firstOrNull { it.timestamp > endTime }
    return buildList {
        if (previous != null) add(previous)
        addAll(inRange)
        if (next != null) add(next)
    }
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
        val y = coords.capacityToY(valueSelector(point))
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

/**
 * 构建温度曲线路径（使用 tempToY 映射，0.9 缩放）
 */
private fun buildTempPath(
    points: List<ChartPoint>,
    coords: ChartCoordinates,
    valueSelector: (ChartPoint) -> Double
): Path {
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = coords.timeToX(point.timestamp)
        val y = coords.tempToY(valueSelector(point))
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

/**
 * 仅绘制垂直网格线
 */
private fun DrawScope.drawVerticalGridLines(
    coords: ChartCoordinates,
    gridColor: Color,
    verticalSegments: Int
) {
    val cols = verticalSegments.coerceAtLeast(1)
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
) {
    val textPaint = createTextPaint(gridColor.toArgb(), 24f)
    val minW = coords.minPower.roundToInt()
    val maxW = coords.maxPower.roundToInt()
    val minor = minorStepW.coerceAtLeast(1)
    val major = majorStepW.coerceAtLeast(minor)

    // 仅绘制主刻度标签
    var value = minW
    while (value <= maxW) {
        if (value % major == 0) {
            val y = coords.powerToY(value.toDouble())
            val powerText = String.format(Locale.getDefault(), "%.0f W", (value * labelSignMultiplier).toDouble())
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
    timeLabelFormatter: (Long) -> String,
    totalSegments: Int,
    labelStep: Int,
) {
    val textPaint = createTextPaint(gridColor.toArgb(), 24f)
    val cols = totalSegments.coerceAtLeast(1)
    val step = labelStep.coerceAtLeast(1)
    val colStep = coords.chartWidth / cols

    for (i in 0..cols) {
        if (i % step != 0 && i != cols) continue
        val x = coords.paddingLeft + colStep * i
        val timeValue = coords.minTime + (coords.timeRange * i / cols).toLong()
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

/** 温度轴范围：按数据自动扩展，20℃ 步进，无默认范围与硬限制。 */
private fun computeTempAxisRange(points: List<ChartPoint>): Pair<Double, Double> {
    val validTemps = points.asSequence()
        .map { it.temp.toDouble() }
        .filter { it > 0.0 }
        .toList()
    if (validTemps.isEmpty()) return 0.0 to TEMP_EXPAND_STEP_TENTHS

    val observedMin = validTemps.min()
    val observedMax = validTemps.max()
    val minTemp = kotlin.math.floor(observedMin / TEMP_EXPAND_STEP_TENTHS) * TEMP_EXPAND_STEP_TENTHS
    val maxTemp = kotlin.math.ceil(observedMax / TEMP_EXPAND_STEP_TENTHS) * TEMP_EXPAND_STEP_TENTHS

    return if (maxTemp - minTemp < 1.0) {
        minTemp to (minTemp + TEMP_EXPAND_STEP_TENTHS)
    } else {
        minTemp to maxTemp
    }
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
 * 绘制温度极值标记（最高/最低点圆点+标签）
 */
private fun DrawScope.drawTempExtremeMarkers(
    points: List<ChartPoint>,
    coords: ChartCoordinates,
    tempColor: Color,
) {
    val validPoints = points.filter { it.temp > 0 }
    if (validPoints.size < 2) return
    val maxPoint = validPoints.maxByOrNull { it.temp } ?: return
    val minPoint = validPoints.minByOrNull { it.temp } ?: return
    if (maxPoint.temp == minPoint.temp) return

    val textPaint = createTextPaint(tempColor.toArgb(), 20f)
    val padding = 6.dp.toPx()
    val textHeight = -textPaint.fontMetrics.ascent
    val chartRight = coords.paddingLeft + coords.chartWidth
    val scaledChartHeight = coords.chartHeight * 0.9f
    val chartBottom = coords.paddingTop + scaledChartHeight

    for (point in listOf(maxPoint, minPoint)) {
        val x = coords.timeToX(point.timestamp)
        val y = coords.tempToY(point.temp.toDouble())

        drawCircle(tempColor, radius = 3.dp.toPx() * 0.65f, center = Offset(x, y))

        val label = String.format(Locale.getDefault(), "%.1f ℃", point.temp / 10.0)
        val labelWidth = textPaint.measureText(label)
        var textX = x + padding
        if (textX + labelWidth > chartRight) textX = x - padding - labelWidth
        if (textX < coords.paddingLeft) textX = coords.paddingLeft

        val isMax = point === maxPoint
        val textY = if (isMax) y - padding else y + textHeight + padding

        drawContext.canvas.nativeCanvas.drawText(label, textX, textY, textPaint)
    }
}
