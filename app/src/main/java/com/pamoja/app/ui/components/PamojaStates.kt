package com.pamoja.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * Nothing here yet, and that is fine.
 *
 * An empty state is an invitation, not a failure, so it carries a primary
 * action rather than just explaining the absence.
 */
@Composable
fun PamojaEmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalPamojaColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x8, vertical = Spacing.x10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(PamojaRadii.lg))
                .background(colors.accentPrimarySubtle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.accentPrimary,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.x5))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.x2))

        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Spacing.x6))
            Button(
                onClick = onAction,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                ),
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Something failed.
 *
 * Takes the throwable rather than a string so the caller cannot accidentally
 * pass `exception.message`, which is exactly the habit this replaces. Retry is
 * shown only when [ErrorCopy] says retrying can actually help.
 */
@Composable
fun PamojaErrorState(
    error: Throwable,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val colors = LocalPamojaColors.current
    val copy = error.toErrorCopy()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x8, vertical = Spacing.x10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(PamojaRadii.lg))
                .background(colors.statusDangerSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(PamojaIcons.AlertCircle),
                contentDescription = null,
                tint = colors.statusDanger,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.x5))

        Text(
            text = copy.title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.x2))

        Text(
            text = copy.body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        if (copy.retryLabel != null && onRetry != null) {
            Spacer(modifier = Modifier.height(Spacing.x6))
            Button(
                onClick = onRetry,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                ),
            ) {
                Text(text = copy.retryLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Persistent offline notice.
 *
 * Sits inline at the top of a screen rather than floating over content, because
 * being offline is a condition rather than an event and a snackbar would
 * disappear while the condition is still true.
 */
@Composable
fun OfflineBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier,
    lastUpdatedLabel: String? = null,
) {
    val colors = LocalPamojaColors.current

    AnimatedVisibility(
        visible = isOffline,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.statusWarningSubtle)
                .padding(horizontal = Spacing.x4, vertical = Spacing.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(PamojaIcons.AlertCircle),
                contentDescription = null,
                tint = colors.statusWarning,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.x3))
            Column {
                Text(
                    text = "You are offline",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
                // Staleness is the thing people actually need to judge. "Offline"
                // alone leaves them wondering whether what they see is current.
                if (lastUpdatedLabel != null) {
                    Text(
                        text = lastUpdatedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Confirmation for anything destructive.
 *
 * One pattern so a delete never looks like a save. The confirm button is
 * danger-coloured and the cancel is the quieter of the two, which is the right
 * way round: the safe choice should be the easy one.
 */
@Composable
fun PamojaConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true,
    cancelLabel: String = "Cancel",
) {
    val colors = LocalPamojaColors.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface3,
        shape = RoundedCornerShape(PamojaRadii.xl),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = if (isDestructive) colors.statusDanger else colors.textPrimary,
            )
        },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(PamojaRadii.sm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) colors.statusDanger else colors.accentPrimary,
                ),
            ) {
                Text(
                    text = confirmLabel,
                    color = colors.textOnBrand,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = cancelLabel, color = colors.textSecondary)
            }
        },
    )
}
