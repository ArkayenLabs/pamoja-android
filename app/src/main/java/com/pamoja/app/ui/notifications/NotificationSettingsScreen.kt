package com.pamoja.app.ui.notifications

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pamoja.app.R
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing
import com.pamoja.app.util.NotificationCategory

/**
 * Per-category control and a quiet window.
 *
 * One switch for everything is the design that gets an app muted forever:
 * annoyance at daily reminders takes the goal-achieved notification down with
 * it, and once someone mutes at the OS level that channel is gone for good.
 * Splitting them means the low-value nudge can be turned off without losing the
 * message people actually want.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Re-checked on resume rather than at construction, because the usual way
    // to reach this screen is to come back from system settings having just
    // changed the very thing it reports.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onSystemPermissionChanged(
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Spacing.x4, vertical = Spacing.x2),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(PamojaIcons.ArrowLeft),
                        contentDescription = stringResource(R.string.common_back),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.notif_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
            }

            Text(
                text = stringResource(R.string.notif_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.x6),
            )

            Spacer(modifier = Modifier.height(Spacing.x5))

            // The OS decision outranks everything on this screen, so it is
            // stated before the controls it would override.
            if (!uiState.systemPermissionGranted) {
                Column(modifier = Modifier.padding(horizontal = Spacing.x6)) {
                    PamojaNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = stringResource(R.string.notif_blocked_title),
                        body = stringResource(R.string.notif_blocked_body),
                        tone = NoticeTone.Warning,
                    )
                    Spacer(modifier = Modifier.height(Spacing.x3))
                    Text(
                        text = stringResource(R.string.notif_blocked_action),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(PamojaRadii.sm))
                            .clickable { context.openAppNotificationSettings() }
                            .padding(vertical = Spacing.x3, horizontal = Spacing.x2),
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.x5))
            } else if (uiState.allMuted) {
                Column(modifier = Modifier.padding(horizontal = Spacing.x6)) {
                    PamojaNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = stringResource(R.string.notif_all_muted_title),
                        body = stringResource(R.string.notif_all_muted_body),
                        tone = NoticeTone.Neutral,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.x5))
            }

            SectionLabel(stringResource(R.string.notif_section_types))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.x6)
                    .clip(RoundedCornerShape(PamojaRadii.md))
                    .background(colors.surface1)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.md))
                    .padding(Spacing.x4),
                verticalArrangement = Arrangement.spacedBy(Spacing.x5),
            ) {
                // Ordered by how much people want them, so the one most likely
                // to be switched off sits furthest from the one that matters.
                CategoryToggle(
                    category = NotificationCategory.ACHIEVEMENT,
                    title = R.string.notif_achievement_title,
                    subtitle = R.string.notif_achievement_sub,
                    enabled = uiState.isEnabled(NotificationCategory.ACHIEVEMENT),
                    onToggle = viewModel::setCategoryEnabled,
                )
                CategoryToggle(
                    category = NotificationCategory.GROUP_ACTIVITY,
                    title = R.string.notif_group_title,
                    subtitle = R.string.notif_group_sub,
                    enabled = uiState.isEnabled(NotificationCategory.GROUP_ACTIVITY),
                    onToggle = viewModel::setCategoryEnabled,
                )
                CategoryToggle(
                    category = NotificationCategory.RECAP,
                    title = R.string.notif_recap_title,
                    subtitle = R.string.notif_recap_sub,
                    enabled = uiState.isEnabled(NotificationCategory.RECAP),
                    onToggle = viewModel::setCategoryEnabled,
                )
                CategoryToggle(
                    category = NotificationCategory.REMINDER,
                    title = R.string.notif_reminder_title,
                    subtitle = R.string.notif_reminder_sub,
                    enabled = uiState.isEnabled(NotificationCategory.REMINDER),
                    onToggle = viewModel::setCategoryEnabled,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x6))

            SectionLabel(stringResource(R.string.notif_section_quiet))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.x6)
                    .clip(RoundedCornerShape(PamojaRadii.md))
                    .background(colors.surface1)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.md))
                    .padding(Spacing.x4),
            ) {
                Text(
                    text = stringResource(R.string.notif_quiet_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(Spacing.x4))

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                    TimeField(
                        label = stringResource(R.string.notif_quiet_from),
                        minuteOfDay = uiState.quietStartMinute,
                        modifier = Modifier.weight(1f),
                        onPicked = { viewModel.setQuietHours(it, uiState.quietEndMinute) },
                    )
                    TimeField(
                        label = stringResource(R.string.notif_quiet_to),
                        minuteOfDay = uiState.quietEndMinute,
                        modifier = Modifier.weight(1f),
                        onPicked = { viewModel.setQuietHours(uiState.quietStartMinute, it) },
                    )
                }

                // Equal times would silence the app permanently, which nobody
                // means to do by fiddling with a picker.
                if (uiState.quietStartMinute == uiState.quietEndMinute) {
                    Spacer(modifier = Modifier.height(Spacing.x3))
                    Text(
                        text = stringResource(R.string.notif_quiet_all_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.statusWarning,
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(Spacing.x10)
            )
        }
    }
}

/**
 * Icon per category.
 *
 * Deliberately mirrors the one in ActivityScreen rather than sharing it: these
 * are two different enums that happen to have identical constants, one in
 * `domain.model` for logged items and one in `util` for the engine. Unifying
 * them is a real cleanup but not this change's business.
 */
private fun NotificationCategory.icon(): Int = when (this) {
    NotificationCategory.ACHIEVEMENT -> PamojaIcons.Trophy
    NotificationCategory.GROUP_ACTIVITY -> PamojaIcons.Users
    NotificationCategory.REMINDER -> PamojaIcons.Footprints
    NotificationCategory.RECAP -> PamojaIcons.Clock
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalPamojaColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textTertiary,
        modifier = Modifier.padding(
            start = Spacing.x6, end = Spacing.x6, bottom = Spacing.x2
        ),
    )
}

@Composable
private fun CategoryToggle(
    category: NotificationCategory,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    enabled: Boolean,
    onToggle: (NotificationCategory, Boolean) -> Unit,
) {
    val colors = LocalPamojaColors.current

    // The category's tint follows whether it is on. A muted category showing a
    // full-colour chip reads as active at a glance, which is the opposite of
    // what the switch beside it says.
    val accent = if (enabled) colors.accentPrimary else colors.textTertiary
    val chipBackground = if (enabled) colors.accentPrimarySubtle else colors.surface2

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(PamojaRadii.md))
                .background(chipBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(category.icon()),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(Spacing.x3))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                // Says what arrives, not just what it is called, so the choice
                // can be made without switching it on to find out.
                text = stringResource(subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        Spacer(modifier = Modifier.width(Spacing.x3))

        Switch(
            checked = enabled,
            onCheckedChange = { onToggle(category, it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accentPrimary,
                checkedBorderColor = colors.accentPrimary,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.surface2,
                uncheckedBorderColor = colors.borderDefault,
            ),
        )
    }
}

/** Tappable time, opening the platform picker so it honours 12/24 hour setting. */
@Composable
private fun TimeField(
    label: String,
    minuteOfDay: Int,
    modifier: Modifier = Modifier,
    onPicked: (Int) -> Unit,
) {
    val colors = LocalPamojaColors.current
    val context = LocalContext.current
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(Spacing.x2))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PamojaRadii.sm))
                .background(colors.surfaceInput)
                .border(1.dp, colors.borderDefault, RoundedCornerShape(PamojaRadii.sm))
                .clickable {
                    TimePickerDialog(
                        context,
                        { _, pickedHour, pickedMinute ->
                            onPicked(pickedHour * 60 + pickedMinute)
                        },
                        hour,
                        minute,
                        android.text.format.DateFormat.is24HourFormat(context),
                    ).show()
                }
                .padding(vertical = Spacing.x3, horizontal = Spacing.x4),
        ) {
            Text(
                text = "%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
        }
    }
}

/**
 * Opens Pamoja's own notification settings, not the whole Settings app.
 *
 * No pre-O branch: minSdk is 26, so this action always exists. Falls back to
 * the app details page only if no activity handles it, which some OEM ROMs
 * manage to do.
 */
private fun android.content.Context.openAppNotificationSettings() {
    val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

    runCatching { startActivity(direct) }.onFailure {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        }
    }
}
