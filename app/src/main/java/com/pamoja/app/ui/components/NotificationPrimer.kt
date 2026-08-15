package com.pamoja.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pamoja.app.R
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * Asked before the system notification dialog, never instead of it.
 *
 * Android 13 and later grant exactly one real chance at that dialog. Once
 * someone denies it, every later request returns denied without displaying
 * anything, and the only route back is buried in system settings. The app
 * previously fired it on the first composition of Home, before the user had a
 * group, any steps, or a reason to say yes.
 *
 * So this asks first. "Not now" costs nothing and leaves the one system prompt
 * unspent for a moment when the value is obvious. Only "Turn on" spends it.
 *
 * Framed around what actually arrives rather than around permission: nobody
 * wants notifications, they want to know their group hit the goal.
 */
@Composable
fun NotificationPrimerDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPamojaColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface3,
        shape = RoundedCornerShape(PamojaRadii.xl),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(PamojaRadii.md))
                    .background(colors.accentPrimarySubtle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(PamojaIcons.Bell),
                    contentDescription = null,
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.notif_primer_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.notif_primer_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(Spacing.x4))

                // Three concrete examples rather than a promise to be useful.
                // "We will send you updates" tells nobody anything.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                    PrimerExample(PamojaIcons.Trophy, stringResource(R.string.notif_primer_goal))
                    PrimerExample(PamojaIcons.Users, stringResource(R.string.notif_primer_joined))
                    PrimerExample(PamojaIcons.Medal, stringResource(R.string.notif_primer_overtaken))
                }

                Spacer(modifier = Modifier.height(Spacing.x4))

                Text(
                    // Says the quiet parts up front, because the fear behind a
                    // denial is being pestered daily.
                    text = stringResource(R.string.notif_primer_control),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                shape = RoundedCornerShape(PamojaRadii.sm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                ),
            ) {
                Text(
                    text = stringResource(R.string.notif_primer_allow),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_not_now),
                    color = colors.textSecondary,
                )
            }
        },
    )
}

@Composable
private fun PrimerExample(@DrawableRes icon: Int, text: String) {
    val colors = LocalPamojaColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.accentPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.x3))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}
