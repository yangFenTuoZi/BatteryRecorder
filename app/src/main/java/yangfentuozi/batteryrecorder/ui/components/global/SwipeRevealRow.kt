package yangfentuozi.batteryrecorder.ui.components.global

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun SwipeRevealRow(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    isGroupFirst: Boolean,
    isGroupLast: Boolean,
    modifier: Modifier = Modifier,
    actionSpacing: Dp = 2.dp,
    contentBackgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceBright,
    startActionBackgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    endActionBackgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.error,
    startActionContent: (@Composable BoxScope.() -> Unit)? = null,
    endActionContent: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
    onContentClick: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val settleSpec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)

    val minHeightDp = 56.dp
    val minHeightPx = with(density) { minHeightDp.toPx() }

    var contentHeightPx by remember { mutableFloatStateOf(minHeightPx) }
    val targetRevealPx = max(minHeightPx, contentHeightPx)
    val hasStartAction = startActionContent != null
    val hasEndAction = endActionContent != null
    val startTargetRevealPx = if (hasStartAction) targetRevealPx else 0f
    val endTargetRevealPx = if (hasEndAction) targetRevealPx else 0f

    // 使用有符号位移统一表达双向滑动：右滑为正（Start），左滑为负（End）
    val offsetPx = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val latestIsOpen by rememberUpdatedState(isOpen)
    val latestOnOpenChange by rememberUpdatedState(onOpenChange)
    val latestOnContentClick by rememberUpdatedState(onContentClick)

    LaunchedEffect(isOpen, startTargetRevealPx, endTargetRevealPx) {
        if (isDragging) return@LaunchedEffect
        val target = when {
            !isOpen -> 0f
            offsetPx.value > 0f && hasStartAction -> startTargetRevealPx
            offsetPx.value < 0f && hasEndAction -> -endTargetRevealPx
            hasEndAction && !hasStartAction -> -endTargetRevealPx
            hasStartAction && !hasEndAction -> startTargetRevealPx
            else -> 0f
        }
        if (offsetPx.targetValue != target) {
            offsetPx.animateTo(
                targetValue = target,
                animationSpec = settleSpec
            )
        }
    }

    val startRevealPx = offsetPx.value.coerceAtLeast(0f)
    val endRevealPx = (-offsetPx.value).coerceAtLeast(0f)

    val startProgress = if (startTargetRevealPx > 0f) {
        (startRevealPx / startTargetRevealPx).coerceIn(0f, 1f)
    } else 0f
    val endProgress = if (endTargetRevealPx > 0f) {
        (endRevealPx / endTargetRevealPx).coerceIn(0f, 1f)
    } else 0f

    val rowHeightDp = with(density) { targetRevealPx.toDp() }
    val startRevealDp = with(density) { startRevealPx.toDp() }
    val endRevealDp = with(density) { endRevealPx.toDp() }
    val actionSpacingPx = with(density) { actionSpacing.toPx() }
    val startSpacingPx = actionSpacingPx * startProgress
    val endSpacingPx = actionSpacingPx * endProgress
    val translationX = when {
        offsetPx.value > 0f -> offsetPx.value + startSpacingPx
        offsetPx.value < 0f -> offsetPx.value - endSpacingPx
        else -> 0f
    }
    val startSpacingDp = with(density) { startSpacingPx.toDp() }
    val endSpacingDp = with(density) { endSpacingPx.toDp() }

    val isStartSplit = startProgress > 0f
    val isEndSplit = endProgress > 0f
    val contentShape = splicedCornerRadius(
        isVerticalFirst = isGroupFirst,
        isVerticalLast = isGroupLast,
        isHorizontalFirst = !isStartSplit,
        isHorizontalLast = !isEndSplit
    )
    val startShape = splicedCornerRadius(
        isVerticalFirst = isGroupFirst,
        isVerticalLast = isGroupLast,
        isHorizontalFirst = true,
        isHorizontalLast = false
    )
    val endShape = splicedCornerRadius(
        isVerticalFirst = isGroupFirst,
        isVerticalLast = isGroupLast,
        isHorizontalFirst = false,
        isHorizontalLast = true
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp)
            .pointerInput(startTargetRevealPx, endTargetRevealPx, hasStartAction, hasEndAction) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        latestOnOpenChange(true)
                        scope.launch { offsetPx.stop() }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()

                        if (dragAmount > 0f && !hasStartAction && offsetPx.value >= 0f) {
                            return@detectHorizontalDragGestures
                        }
                        if (dragAmount < 0f && !hasEndAction && offsetPx.value <= 0f) {
                            return@detectHorizontalDragGestures
                        }

                        if (!latestIsOpen) latestOnOpenChange(true)
                        val next = (offsetPx.value + dragAmount).coerceIn(
                            -endTargetRevealPx,
                            startTargetRevealPx
                        )
                        scope.launch { offsetPx.snapTo(next) }
                    },
                    onDragEnd = {
                        isDragging = false
                        val target = when {
                            offsetPx.value > 0f && hasStartAction && offsetPx.value >= startTargetRevealPx * 0.5f -> startTargetRevealPx
                            offsetPx.value < 0f && hasEndAction && -offsetPx.value >= endTargetRevealPx * 0.5f -> -endTargetRevealPx
                            else -> 0f
                        }
                        latestOnOpenChange(target != 0f)
                        scope.launch {
                            offsetPx.animateTo(
                                targetValue = target,
                                animationSpec = settleSpec
                            )
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        val target = when {
                            latestIsOpen && offsetPx.value > 0f && hasStartAction -> startTargetRevealPx
                            latestIsOpen && offsetPx.value < 0f && hasEndAction -> -endTargetRevealPx
                            else -> 0f
                        }
                        latestOnOpenChange(target != 0f)
                        scope.launch {
                            offsetPx.animateTo(
                                targetValue = target,
                                animationSpec = settleSpec
                            )
                        }
                    }
                )
            }
    ) {
        if (hasStartAction) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(startRevealDp + startSpacingDp)
                    .height(rowHeightDp)
                    .background(MaterialTheme.colorScheme.background)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(startRevealDp)
                    .height(rowHeightDp)
                    .clip(startShape)
                    .background(startActionBackgroundColor),
                content = startActionContent!!
            )
        }
        if (hasEndAction) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(endRevealDp + endSpacingDp)
                    .height(rowHeightDp)
                    .background(MaterialTheme.colorScheme.background)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(endRevealDp)
                    .height(rowHeightDp)
                    .clip(endShape)
                    .background(endActionBackgroundColor),
                content = endActionContent!!
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { this.translationX = translationX }
                .clip(contentShape)
                .background(contentBackgroundColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (latestIsOpen) {
                                latestOnOpenChange(false)
                            } else {
                                latestOnContentClick()
                            }
                        }
                    )
                }
                .onSizeChanged { size ->
                    contentHeightPx = max(minHeightPx, size.height.toFloat())
                }
        ) {
            content()
        }
    }
}
