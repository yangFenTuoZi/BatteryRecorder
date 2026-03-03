package yangfentuozi.batteryrecorder.ui.components.home

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.ui.components.global.StatRow
import yangfentuozi.batteryrecorder.ui.viewmodel.LivePowerPoint
import yangfentuozi.batteryrecorder.utils.computePowerW
import yangfentuozi.batteryrecorder.utils.formatDateTime
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatPower
import kotlin.math.abs
import kotlin.math.max

// 图表绘制常量
private const val GLOW_MAX_ALPHA = 0.25f                    // 渐变填充区域最大透明度
private const val GLOW_CLIP_TOLERANCE_PX = 5f               // 发光效果裁剪容差（像素）
private const val OUTER_GLOW_STROKE_MULTIPLIER = 2.8f       // 外层发光描边宽度倍数
private const val OUTER_GLOW_ALPHA = 0.16f                  // 外层发光透明度
private const val OUTER_GLOW_BLUR_MULTIPLIER = 2.2f         // 外层发光模糊半径倍数
private const val INNER_GLOW_STROKE_MULTIPLIER = 1.9f       // 内层发光描边宽度倍数
private const val INNER_GLOW_ALPHA = 0.26f                  // 内层发光透明度
private const val INNER_GLOW_BLUR_MULTIPLIER = 1.4f         // 内层发光模糊半径倍数
private const val LINE_STROKE_WIDTH_MULTIPLIER = 0.8f       // 主线条描边宽度倍数
private const val LAST_POINT_OUTER_RADIUS = 20f             // 最新数据点外圈半径
private const val LAST_POINT_INNER_RADIUS = 12f             // 最新数据点内圈半径
private const val LAST_POINT_OUTER_ALPHA = 0.6f             // 最新数据点外圈透明度
private const val LAST_POINT_INNER_ALPHA = 0.9f             // 最新数据点内圈透明度

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
    val isDischargingNow = livePoints.lastOrNull()?.status == BatteryStatus.Discharging
    val chargeStatusText = if (isDischargingNow) "放电" else "充电"
    val averageLabel = if (isDischargingNow) "平均功耗" else "平均功率"
    val currentLabel = if (isDischargingNow) "当前功耗" else "当前功率"

    Column(
        modifier = modifier
            .clickable(enabled = record != null && onClick != null) { onClick?.invoke() }
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "当前记录" + if (record != null) " - $chargeStatusText" else "",
            style = MaterialTheme.typography.titleMedium
        )

        if (record != null) {
            Spacer(Modifier.height(12.dp))
            val stats = record.stats
            val latestPoint = livePoints.lastOrNull()
            val latestPowerRaw = latestPoint?.powerRaw

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatRow("开始时间", formatDateTime(stats.startTime))
                    StatRow(
                        averageLabel,
                        formatPower(
                            powerW = stats.averagePower,
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = calibrationValue
                        )
                    )
                    StatRow("温度", latestPoint?.let { "${it.temp / 10.0}°C" } ?: "--")
                }

                LivePowerChart(
                    points = livePoints,
                    dualCellEnabled = dualCellEnabled,
                    calibrationValue = calibrationValue,
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f, fill = true)) {
                    StatRow("时长", formatDurationHours(stats.endTime - stats.startTime))
                }
                Column(modifier = Modifier.weight(1f, fill = true)) {
                    StatRow(
                        currentLabel,
                        if (latestPowerRaw != null) {
                            val latestStatus = latestPoint.status
                            val displayPowerRaw =
                                if (latestStatus == BatteryStatus.Discharging) {
                                    val absPower = abs(latestPowerRaw.toDouble())
                                    if (dischargeDisplayPositive) -absPower else absPower
                                } else {
                                    latestPowerRaw.toDouble()
                                }
                            formatPower(
                                powerW = displayPowerRaw,
                                dualCellEnabled = dualCellEnabled,
                                calibrationValue = calibrationValue
                            )
                        } else {
                            "--W"
                        }
                    )
                }
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
private fun LivePowerChart(
    points: List<LivePowerPoint>,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
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
                points.map {
                    val powerW = computePowerW(
                        rawPower = it.powerRaw.toDouble(),
                        dualCellEnabled = dualCellEnabled,
                        calibrationValue = calibrationValue
                    )
                    val plotPowerW =
                        if (it.status == BatteryStatus.Discharging) abs(powerW) else powerW
                    LivePowerPointDisplay(plotPowerW)
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = 4.dp.toPx()
                val minPower = displayPoints.minOf { it.power }
                val maxPower = displayPoints.maxOf { it.power }
                val powerRange = max(1e-6, maxPower - minPower)

                val left = padding
                val top = padding
                val right = size.width - padding
                val bottom = size.height - padding
                val chartWidth = right - left
                val chartHeight = bottom - top
                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                val floorMarginPx = 8.dp.toPx()
                val effectiveChartHeight = (chartHeight - floorMarginPx).coerceAtLeast(1f)
                val pointCount = displayPoints.size
                val xStep = if (pointCount > 1) chartWidth / (pointCount - 1) else 0f

                val chartPoints = displayPoints.mapIndexed { index, point ->
                    val x = left + xStep * index
                    val y =
                        top + (1f - ((point.power - minPower) / powerRange).toFloat()) * effectiveChartHeight
                    LiveChartPoint(x, y)
                }

                fun buildSmoothedPath(yOffsetPx: Float = 0f): Path {
                    val path = Path().apply {
                        moveTo(
                            chartPoints.first().x,
                            chartPoints.first().y + yOffsetPx
                        )
                    }
                    for (i in 1 until chartPoints.size) {
                        val previous = chartPoints[i - 1]
                        val current = chartPoints[i]
                        val midX = (previous.x + current.x) / 2f
                        val midY = (previous.y + current.y) / 2f + yOffsetPx
                        path.quadraticTo(previous.x, previous.y + yOffsetPx, midX, midY)
                    }
                    path.lineTo(chartPoints.last().x, chartPoints.last().y + yOffsetPx)
                    return path
                }

                clipRect(left = left, top = top, right = right, bottom = bottom) {
                    val smoothedPath = buildSmoothedPath()
                    val runTop = chartPoints.minOf { it.y }
                    val glowBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to lineColor.copy(alpha = GLOW_MAX_ALPHA),
                            1.0f to Color.Transparent
                        ),
                        startY = runTop,
                        endY = bottom
                    )
                    val fillPath = Path().apply {
                        addPath(smoothedPath)
                        lineTo(chartPoints.last().x, bottom)
                        lineTo(chartPoints.first().x, bottom)
                        close()
                    }
                    drawPath(path = fillPath, brush = glowBrush)
                }

                val lineStrokeWidth = 3.dp.toPx() * LINE_STROKE_WIDTH_MULTIPLIER
                val solidStroke =
                    Stroke(width = lineStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

                clipRect(left = left, top = top, right = right, bottom = bottom) {
                    val path = buildSmoothedPath()
                    val androidPath = path.asAndroidPath()
                    val baseColor = lineColor.toArgb()

                    val clipBoundaryPath = buildSmoothedPath(yOffsetPx = -GLOW_CLIP_TOLERANCE_PX)
                    val underClipPath = Path().apply {
                        addPath(clipBoundaryPath)
                        lineTo(chartPoints.last().x, bottom)
                        lineTo(chartPoints.first().x, bottom)
                        close()
                    }

                    clipPath(path = underClipPath, clipOp = ClipOp.Intersect) {
                        drawIntoCanvas { canvas ->
                            val outerPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                                strokeWidth = lineStrokeWidth * OUTER_GLOW_STROKE_MULTIPLIER
                                color = baseColor
                                alpha = (255 * OUTER_GLOW_ALPHA).toInt().coerceIn(0, 255)
                                maskFilter = BlurMaskFilter(
                                    lineStrokeWidth * OUTER_GLOW_BLUR_MULTIPLIER,
                                    BlurMaskFilter.Blur.NORMAL
                                )
                            }
                            canvas.nativeCanvas.drawPath(androidPath, outerPaint)

                            val innerPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                                strokeWidth = lineStrokeWidth * INNER_GLOW_STROKE_MULTIPLIER
                                color = baseColor
                                alpha = (255 * INNER_GLOW_ALPHA).toInt().coerceIn(0, 255)
                                maskFilter = BlurMaskFilter(
                                    lineStrokeWidth * INNER_GLOW_BLUR_MULTIPLIER,
                                    BlurMaskFilter.Blur.NORMAL
                                )
                            }
                            canvas.nativeCanvas.drawPath(androidPath, innerPaint)
                        }
                    }
                }

                val path = buildSmoothedPath()
                drawPath(path = path, color = lineColor, style = solidStroke)

                val lastPoint = chartPoints.last()
                drawCircle(
                    color = lineColor.copy(alpha = LAST_POINT_OUTER_ALPHA),
                    radius = LAST_POINT_OUTER_RADIUS,
                    center = Offset(lastPoint.x, lastPoint.y)
                )
                drawCircle(
                    color = lineColor.copy(alpha = LAST_POINT_INNER_ALPHA),
                    radius = LAST_POINT_INNER_RADIUS,
                    center = Offset(lastPoint.x, lastPoint.y)
                )
            }
        }
    }
}

private data class LivePowerPointDisplay(
    val power: Double
)

private data class LiveChartPoint(
    val x: Float,
    val y: Float
)
