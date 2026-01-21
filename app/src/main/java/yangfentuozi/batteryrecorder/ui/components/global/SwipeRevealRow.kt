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
    actionBackgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.error,
    actionContent: @Composable BoxScope.() -> Unit,
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

    val revealPx = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var requestedOpenInDrag by remember { mutableStateOf(false) }
    val latestIsOpen by rememberUpdatedState(isOpen)
    val latestOnOpenChange by rememberUpdatedState(onOpenChange)
    val latestOnContentClick by rememberUpdatedState(onContentClick)

    LaunchedEffect(isOpen, targetRevealPx) {
        if (isDragging) return@LaunchedEffect
        val target = if (isOpen) targetRevealPx else 0f
        if (revealPx.targetValue != target) {
            revealPx.animateTo(
                targetValue = target,
                animationSpec = settleSpec
            )
        }
    }

    val progress = if (targetRevealPx > 0f) {
        (revealPx.value / targetRevealPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val rowHeightDp = with(density) { targetRevealPx.toDp() }
    val revealDp = with(density) { revealPx.value.toDp() }
    val spacingPx = with(density) { actionSpacing.toPx() } * progress
    val translationX = -(revealPx.value + spacingPx)
    val spacingDp = with(density) { spacingPx.toDp() }

    val isSplit = progress > 0f
    val leftShape = splicedCornerRadius(
        isVerticalFirst = isGroupFirst,
        isVerticalLast = isGroupLast,
        isHorizontalFirst = true,
        isHorizontalLast = !isSplit
    )
    val rightShape = splicedCornerRadius(
        isVerticalFirst = isGroupFirst,
        isVerticalLast = isGroupLast,
        isHorizontalFirst = false,
        isHorizontalLast = true
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp)
            .pointerInput(targetRevealPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        requestedOpenInDrag = false
                        latestOnOpenChange(true)
                        scope.launch { revealPx.stop() }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()

                        if (!latestIsOpen && !requestedOpenInDrag && dragAmount < 0f) {
                            requestedOpenInDrag = true
                            latestOnOpenChange(true)
                        }

                        if (!latestIsOpen && revealPx.value <= 0f && dragAmount > 0f) {
                            return@detectHorizontalDragGestures
                        }

                        val next = (revealPx.value - dragAmount).coerceIn(0f, targetRevealPx)
                        scope.launch { revealPx.snapTo(next) }
                    },
                    onDragEnd = {
                        isDragging = false
                        val targetOpen = revealPx.value >= targetRevealPx * 0.5f
                        latestOnOpenChange(targetOpen)
                        scope.launch {
                            revealPx.animateTo(
                                targetValue = if (targetOpen) targetRevealPx else 0f,
                                animationSpec = settleSpec
                            )
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        val targetOpen = latestIsOpen
                        latestOnOpenChange(targetOpen)
                        scope.launch {
                            revealPx.animateTo(
                                targetValue = if (targetOpen) targetRevealPx else 0f,
                                animationSpec = settleSpec
                            )
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealDp + spacingDp)
                .height(rowHeightDp)
                .background(MaterialTheme.colorScheme.background)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealDp)
                .height(rowHeightDp)
                .clip(rightShape)
                .background(actionBackgroundColor),
            content = actionContent
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { this.translationX = translationX }
                .clip(leftShape)
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
