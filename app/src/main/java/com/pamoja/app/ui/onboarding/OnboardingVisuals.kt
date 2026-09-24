package com.pamoja.app.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pamoja.app.R
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaElevation
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/** The actual product in miniature: one group, one pooled finish line. */
@Composable
internal fun SharedWeekHero(modifier: Modifier = Modifier) {
    val colors = LocalPamojaColors.current
    val motionEnabled = pamojaMotionEnabled()
    val progress = remember { Animatable(if (motionEnabled) 0.08f else 0.69f) }

    LaunchedEffect(motionEnabled) {
        if (motionEnabled) {
            progress.animateTo(0.69f, animationSpec = tween(380))
        } else {
            progress.snapTo(0.69f)
        }
    }

    val shape = RoundedCornerShape(PamojaRadii.xxl)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(PamojaElevation.raised, shape, spotColor = colors.overlay)
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, shape),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = colors.accentPrimarySubtle,
                radius = size.minDimension * 0.48f,
                center = Offset(size.width * 0.98f, size.height * 0.03f),
            )
            drawCircle(
                color = colors.accentTealSubtle,
                radius = size.minDimension * 0.36f,
                center = Offset(size.width * 0.04f, size.height * 1.02f),
            )
        }

        Column(modifier = Modifier.padding(Spacing.x5)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.welcome_live_group).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.accentTealSubtle)
                        .padding(horizontal = Spacing.x3, vertical = Spacing.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accentTeal),
                    )
                    Spacer(Modifier.width(Spacing.x2))
                    Text(
                        text = stringResource(R.string.welcome_shared_goal).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accentTeal,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.x6))
            Text(
                text = stringResource(R.string.welcome_demo_steps),
                style = MaterialTheme.typography.displayMedium,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.welcome_demo_target),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(Spacing.x5))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .semantics {
                        contentDescription = "Group progress, 48,320 of 70,000 shared steps"
                    },
            ) {
                drawRoundRect(
                    color = colors.progressTrack,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
                drawRoundRect(
                    color = colors.progressFill,
                    size = Size(size.width * progress.value, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
            }

            Spacer(Modifier.height(Spacing.x5))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    InitialAvatar("A", colors.accentPrimary, colors.textOnBrand)
                    InitialAvatar("J", colors.accentTeal, colors.surfaceSunken)
                    InitialAvatar("M", colors.accentAmber, colors.surfaceSunken)
                }
                Spacer(Modifier.width(Spacing.x3))
                Column {
                    Text(
                        text = stringResource(R.string.welcome_people_moving),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.welcome_steps_remaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun InitialAvatar(
    initial: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(background)
            .border(2.dp, LocalPamojaColors.current.surface1, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** System animator scale zero is Android's reduced-motion signal. */
@Composable
private fun pamojaMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
}
