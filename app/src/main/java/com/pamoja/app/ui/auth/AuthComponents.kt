package com.pamoja.app.ui.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/** Back control shared by every auth screen. */
@Composable
fun AuthBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPamojaColors.current
    IconButton(onClick = onBack, modifier = modifier.size(48.dp)) {
        Icon(
            painter = painterResource(PamojaIcons.ArrowLeft),
            contentDescription = "Back",
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

enum class NoticeTone { Neutral, Info, Danger, Warning, Success }

/**
 * The card used for reassurance, inline failures and conflict states.
 *
 * Failures appear here rather than in a snackbar so they stay on screen while
 * the user acts on them, and so they sit next to the thing that failed.
 */
@Composable
fun AuthNotice(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    tone: NoticeTone = NoticeTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current

    val (container, accent) = when (tone) {
        NoticeTone.Neutral -> colors.surfaceSunken to colors.textSecondary
        NoticeTone.Info -> colors.statusInfoSubtle to colors.statusInfo
        NoticeTone.Danger -> colors.statusDangerSubtle to colors.statusDanger
        NoticeTone.Warning -> colors.statusWarningSubtle to colors.statusWarning
        NoticeTone.Success -> colors.statusSuccessSubtle to colors.statusSuccess
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PamojaRadii.md),
        color = container,
    ) {
        Row(modifier = Modifier.padding(Spacing.x4)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.x3))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.x1))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * One sign-in method.
 *
 * [isGoogleMark] renders the icon untinted, because Google's brand rules forbid
 * recolouring the G. Everything else tints to the current content colour.
 *
 * While another method is running this one stays legible but non-interactive,
 * so it is obvious what is happening without blocking the whole screen.
 */
@Composable
fun AuthMethodButton(
    @DrawableRes icon: Int,
    label: String,
    loadingLabel: String,
    onClick: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    filled: Boolean,
    modifier: Modifier = Modifier,
    isGoogleMark: Boolean = false,
) {
    val colors = LocalPamojaColors.current

    val container = if (filled) colors.accentPrimary else Color.Transparent
    val content = if (filled) colors.textOnBrand else colors.textPrimary

    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(PamojaRadii.md),
        color = container,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (filled) Modifier
                    else Modifier.border(
                        width = 1.dp,
                        color = colors.borderDefault,
                        shape = RoundedCornerShape(PamojaRadii.md),
                    )
                )
                .background(
                    if (filled) Color.Transparent else colors.surfaceCanvas,
                    RoundedCornerShape(PamojaRadii.md),
                )
                .padding(horizontal = Spacing.x4),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = content,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = if (isGoogleMark) Color.Unspecified else content,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.x3))

                Text(
                    text = if (isLoading) loadingLabel else label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
            }

            if (!isLoading) {
                Icon(
                    painter = painterResource(PamojaIcons.ChevronRight),
                    contentDescription = null,
                    tint = if (filled) content else colors.textTertiary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(18.dp),
                )
            }
        }
    }
}
