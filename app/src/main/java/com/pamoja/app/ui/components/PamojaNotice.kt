package com.pamoja.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pamoja.app.R
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.PillShape
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
    /** Optional. A notice that states a problem the user can act on now. */
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** Optional. Omit for a notice the user should not be able to hide. */
    onDismiss: (() -> Unit)? = null,
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
            Column(modifier = Modifier.weight(1f)) {
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

                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(Spacing.x3))
                    Button(
                        onClick = onAction,
                        shape = PillShape,
                        contentPadding = PaddingValues(
                            horizontal = Spacing.x4,
                            vertical = Spacing.x2,
                        ),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textOnBrand,
                        )
                    }
                }
            }

            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        painter = painterResource(PamojaIcons.Close),
                        contentDescription = stringResource(R.string.common_dismiss),
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
