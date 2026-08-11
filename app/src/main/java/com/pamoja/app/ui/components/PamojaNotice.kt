package com.pamoja.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

enum class NoticeTone { Neutral, Info, Danger, Warning, Success }

/**
 * The card used for reassurance, inline failures and conflict states.
 *
 * Failures appear here rather than in a snackbar so they stay on screen while
 * the user acts on them, and so they sit next to the thing that failed.
 */
@Composable
fun PamojaNotice(
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
