package com.pamoja.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

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

/**
 * Placeholder for the group list on Home.
 *
 * Matches the real card's height and rhythm so the page does not jump when
 * content arrives. That jump is the reason a bare spinner feels cheap: the
 * layout it implies is nothing like the layout that follows.
 */
@Composable
fun GroupListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        repeat(itemCount) {
            val colors = LocalPamojaColors.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PamojaRadii.md))
                    .background(colors.surface1)
                    .padding(Spacing.x4)
            ) {
                Column {
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.55f),
                        height = 18.dp,
                    )
                    Spacer(modifier = Modifier.height(Spacing.x3))
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.35f),
                        height = 13.dp,
                    )
                    Spacer(modifier = Modifier.height(Spacing.x4))
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(),
                        height = 8.dp,
                        shape = CircleShape,
                    )
                }
            }
        }
    }
}

/** Placeholder for a leaderboard row: rank, avatar, name, step count. */
@Composable
fun LeaderboardSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        repeat(itemCount) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(
                    modifier = Modifier.width(20.dp),
                    height = 14.dp,
                )
                Spacer(modifier = Modifier.width(Spacing.x3))
                SkeletonBlock(
                    modifier = Modifier.size(36.dp),
                    height = 36.dp,
                    shape = CircleShape,
                )
                Spacer(modifier = Modifier.width(Spacing.x3))
                // Widths vary so the placeholder does not read as a table of
                // identical bars, which is the tell that makes skeletons look fake.
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth(
                        when (index % 3) {
                            0 -> 0.45f
                            1 -> 0.32f
                            else -> 0.38f
                        }
                    ),
                    height = 15.dp,
                )
            }
        }
    }
}

/** Placeholder for the group dashboard: progress ring, then the leaderboard. */
@Composable
fun GroupDashboardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Spacing.x8))

        SkeletonBlock(
            modifier = Modifier.size(180.dp),
            height = 180.dp,
            shape = CircleShape,
        )

        Spacer(modifier = Modifier.height(Spacing.x6))

        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.5f), height = 16.dp)

        Spacer(modifier = Modifier.height(Spacing.x8))

        LeaderboardSkeleton()
    }
}
