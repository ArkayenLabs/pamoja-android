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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.PillShape
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
    val context = LocalContext.current

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
            text = stringResource(copy.title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.x2))

        Text(
            text = copy.body(context),
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
                Text(text = stringResource(copy.retryLabel), style = MaterialTheme.typography.labelLarge)
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
                    text = stringResource(R.string.offline_banner_title),
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
    cancelLabel: String = stringResource(R.string.common_cancel),
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
                // Pill, like every other button in the app. This used to be
                // Radii.sm, the chip radius, which is why dialogs read as
                // belonging to a different app than the screen behind them.
                shape = PillShape,
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

/**
 * Confirmation for the irreversible end of the scale: deleting an account.
 *
 * Separate from [PamojaConfirmDialog] because a single tap is the wrong shape
 * of gesture for something with no undo. Two things are different here:
 *
 *  - Consequences are itemised rather than summarised. "Your data will be
 *    removed" does not tell anyone what they are about to lose; a list of the
 *    actual things does.
 *  - The confirm button stays disabled until the user types the confirmation
 *    word. The point is not security, it is that it cannot be done absent
 *    mindedly, which is exactly how account deletions get regretted.
 *
 * Matching is trimmed and case-insensitive. Someone who typed "delete " with a
 * trailing space, or whose keyboard auto-capitalised, has demonstrated intent
 * just as clearly as someone who typed it exactly.
 */
@Composable
fun PamojaDestructiveConfirmDialog(
    title: String,
    body: String,
    consequences: List<String>,
    confirmationWord: String,
    confirmationHint: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = stringResource(R.string.common_cancel),
) {
    val colors = LocalPamojaColors.current
    var typed by rememberSaveable { mutableStateOf("") }
    val unlocked = typed.trim().equals(confirmationWord, ignoreCase = true)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface3,
        shape = RoundedCornerShape(PamojaRadii.xl),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.statusDanger,
            )
        },
        text = {
            Column {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                Spacer(Modifier.height(Spacing.x4))

                consequences.forEach { line ->
                    Row(
                        modifier = Modifier.padding(bottom = Spacing.x1),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // A dot, not a glyph. The icon set has nothing that
                        // reads as a list marker, and emoji are not an option.
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp, end = Spacing.x2)
                                .size(4.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(colors.statusDanger),
                        )
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.x4))

                PamojaTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = confirmationHint,
                    placeholder = confirmationWord,
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = unlocked,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.statusDanger,
                    disabledContainerColor = colors.statusDanger.copy(alpha = 0.35f),
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
