package com.pamoja.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaRadii

/**
 * One shimmering placeholder block.
 *
 * The shimmer is a moving gradient rather than a pulsing opacity because a
 * pulse at this size reads as something blinking at you, while a sweep reads
 * as loading. Kept slow enough not to draw attention away from content that
 * has already arrived.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(PamojaRadii.xs),
) {
    val colors = LocalPamojaColors.current

    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    // Travels well past the edges so the highlight enters and leaves cleanly
    // instead of appearing to be born in the middle of the block.
    val start = -400f + progress * 1_400f

    val brush = Brush.horizontalGradient(
        colors = listOf(
            colors.surfaceSunken,
            colors.borderSubtle,
            colors.surfaceSunken,
        ),
        startX = start,
        endX = start + 400f,
    )

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(brush)
    )
}
