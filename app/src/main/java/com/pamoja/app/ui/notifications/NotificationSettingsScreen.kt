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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.ui.kit.ButtonSize
import com.pamoja.app.ui.kit.CardHeader
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.FieldShape
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitInAppTheme
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                // The categories live in system settings now, so returning to
                // this screen is the moment they may have changed.
                viewModel.refreshCategories()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NotificationSettingsContent(
        state = uiState,
        onBack = onBack,
        onOpenApp = { context.openAppNotificationSettings() },
        // A device without the per-channel settings screen would otherwise
        // crash. Rare, but this is a settings row, not worth taking the app down.
        onOpenCategory = { runCatching { context.startActivity(viewModel.settingsIntentFor(it)) } },
        onQuietHours = viewModel::setQuietHours,
    )
}

/** Stateless boundary for previews and tests. */
@Composable
internal fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    onBack: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenCategory: (NotificationCategory) -> Unit,
    onQuietHours: (start: Int, end: Int) -> Unit,
) = KitInAppTheme {
    KitScreen(
        gutter = 20.dp,
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
    ) {
        Text(stringResource(R.string.notif_title), style = Kit.type.headline)
        Text(stringResource(R.string.notif_subtitle), style = Kit.type.body, color = Kit.colors.muted)

        // The OS decision outranks everything on this screen, so it is stated
        // before the controls it would override.
        if (!state.systemPermissionGranted) {
            KitNotice(
                title = stringResource(R.string.notif_blocked_title),
                body = stringResource(R.string.notif_blocked_body),
                danger = true,
                actions = {
                    PrimaryButton(stringResource(R.string.notif_blocked_action), onClick = onOpenApp, size = ButtonSize.Small)
                },
            )
        } else if (state.allMuted) {
            KitNotice(
                title = stringResource(R.string.notif_all_muted_title),
                body = stringResource(R.string.notif_all_muted_body),
            )
        }

        KitCard(spacing = 0.dp) {
            CardHeader(stringResource(R.string.notif_section_types))
            Text(
                stringResource(R.string.notif_types_hint),
                style = Kit.type.caption,
                color = Kit.colors.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            // Ordered by how much people want them, so the one most likely to
            // be switched off sits furthest from the one that matters.
            CategoryRow(NotificationCategory.ACHIEVEMENT, R.string.notif_achievement_title, R.string.notif_achievement_sub, state, onOpenCategory)
            CategoryRow(NotificationCategory.GROUP_ACTIVITY, R.string.notif_group_title, R.string.notif_group_sub, state, onOpenCategory)
            CategoryRow(NotificationCategory.RECAP, R.string.notif_recap_title, R.string.notif_recap_sub, state, onOpenCategory)
            CategoryRow(NotificationCategory.REMINDER, R.string.notif_reminder_title, R.string.notif_reminder_sub, state, onOpenCategory, showDivider = false)
        }

        KitCard {
            CardHeader(stringResource(R.string.notif_section_quiet))
            Text(stringResource(R.string.notif_quiet_body), style = Kit.type.caption, color = Kit.colors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(
                    label = stringResource(R.string.notif_quiet_from),
                    minuteOfDay = state.quietStartMinute,
                    modifier = Modifier.weight(1f),
                    onPicked = { onQuietHours(it, state.quietEndMinute) },
                )
                TimeField(
                    label = stringResource(R.string.notif_quiet_to),
                    minuteOfDay = state.quietEndMinute,
                    modifier = Modifier.weight(1f),
                    onPicked = { onQuietHours(state.quietStartMinute, it) },
                )
            }
            // Equal times would silence the app permanently, which nobody
            // means to do by fiddling with a picker.
            if (state.quietStartMinute == state.quietEndMinute) {
                Text(stringResource(R.string.notif_quiet_all_day), style = Kit.type.caption, color = Kit.colors.danger)
            }
        }
    }
}

/**
 * One category, showing the real state of its Android notification channel.
 *
 * Android owns notification-channel state once a channel exists, so this row
 * reports the real state and opens the one place it can actually be changed.
 * A switch would imply an in-app toggle and feel broken when Android settings
 * opened instead.
 */
@Composable
private fun CategoryRow(
    category: NotificationCategory,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    state: NotificationSettingsUiState,
    onOpen: (NotificationCategory) -> Unit,
    showDivider: Boolean = true,
) {
    val colors = Kit.colors
    val enabled = state.isEnabled(category)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .clickable(role = Role.Button) { onOpen(category) }
                .padding(vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (enabled) colors.iconBackground else colors.track),
            ) {
                Icon(
                    painter = painterResource(category.icon()),
                    contentDescription = null,
                    tint = if (enabled) colors.ink else colors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), style = Kit.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold))
                // Says what arrives, not just what it is called, so the choice
                // can be made without switching it on to find out.
                Text(stringResource(subtitle), style = Kit.type.caption, color = colors.muted)
            }
            // On in lime, Off on the track: the word carries it, the colour
            // only repeats it.
            Text(
                stringResource(if (enabled) R.string.notif_status_on else R.string.notif_status_off),
                style = Kit.type.caption.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) colors.onPop else colors.muted,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (enabled) colors.pop else colors.track)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Icon(
                painter = painterResource(PamojaIcons.ChevronRight),
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(18.dp),
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line),
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

/** Tappable time, opening the platform picker so it honours the 12/24 hour setting. */
@Composable
private fun TimeField(
    label: String,
    minuteOfDay: Int,
    modifier: Modifier = Modifier,
    onPicked: (Int) -> Unit,
) {
    val context = LocalContext.current
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Kit.type.label)
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clip(FieldShape)
                .background(Kit.colors.field)
                .border(1.5.dp, Kit.colors.fieldBorder, FieldShape)
                .clickable(role = Role.Button) {
                    TimePickerDialog(
                        context,
                        { _, pickedHour, pickedMinute -> onPicked(pickedHour * 60 + pickedMinute) },
                        hour,
                        minute,
                        android.text.format.DateFormat.is24HourFormat(context),
                    ).show()
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                "%02d:%02d".format(hour, minute),
                style = Kit.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
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
