package yangfentuozi.batteryrecorder.ui.compose.components.global

import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle

internal fun splicedCornerRadius(
    isVerticalFirst: Boolean,
    isVerticalLast: Boolean,
    isHorizontalFirst: Boolean = true,
    isHorizontalLast: Boolean = true
): ContinuousRoundedRectangle {
    val topStart = if (isVerticalFirst && isHorizontalFirst) 16.dp else 6.dp
    val topEnd = if (isVerticalFirst && isHorizontalLast) 16.dp else 6.dp
    val bottomStart = if (isVerticalLast && isHorizontalLast) 16.dp else 6.dp
    val bottomEnd = if (isVerticalLast && isHorizontalFirst) 16.dp else 6.dp

    return ContinuousRoundedRectangle(topStart, topEnd, bottomStart, bottomEnd)
}

