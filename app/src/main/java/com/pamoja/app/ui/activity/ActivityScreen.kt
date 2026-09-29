package com.pamoja.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.model.ActivityItem
import com.pamoja.app.domain.model.NotificationCategory
import com.pamoja.app.ui.kit.ButtonSize
import com.pamoja.app.ui.kit.CardShape
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitDialog
import com.pamoja.app.ui.kit.KitInAppTheme
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

/**
 * Everything Pamoja has told this person, after the tray forgot it.
 *
 * The tray is not a record: Android clears it on reboot, a swipe destroys the
 * message, and notifications that arrive overnight are usually cleared in one
 * gesture with everything else. Someone whose group hit its weekly goal while
 * they slept previously had no way to ever learn that, which is the single
 * message this product exists to deliver.
 */
@Composable
fun ActivityScreen(
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Opening the list is what marks it seen.
    LaunchedEffect(Unit) { viewModel.onOpened() }

    ActivityContent(
        isLoading = uiState.isLoading,
        items = uiState.items,
        onBack = onBack,
        onOpenGroup = onOpenGroup,
        onClear = viewModel::clearAll,
    )
}

/** Stateless boundary for previews and tests. */
@Composable
internal fun ActivityContent(
    isLoading: Boolean,
    items: List<ActivityItem>,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onClear: () -> Unit,
) = KitInAppTheme {
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    KitScreen(
        gutter = 20.dp,
        top = {
            TopRow(
                start = {
                    CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.activity_back_desc), onClick = onBack)
                },
                // Only offered when there is something to clear.
                end = {
                    if (items.isNotEmpty()) {
                        SecondaryButton(
                            stringResource(R.string.activity_clear),
                            onClick = { confirmClear = true },
                            size = ButtonSize.Small,
                        )
                    }
                },
            )
        },
    ) {
        Text(stringResource(R.string.activity_title), style = Kit.type.headline)

        when {
            isLoading -> repeat(3) { KitSkeleton(height = 72.dp) }

            items.isEmpty() -> KitCard(spacing = 6.dp) {
                Icon(
                    painter = painterResource(PamojaIcons.Bell),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(stringResource(R.string.activity_empty_title), style = Kit.type.title)
                Text(stringResource(R.string.activity_empty_body), style = Kit.type.body, color = Kit.colors.muted)
            }

            else -> KitCard(spacing = 0.dp, padding = 6.dp) {
                items.forEachIndexed { index, item ->
                    ActivityRow(
                        item = item,
                        onClick = item.groupId?.let { { onOpenGroup(it) } },
                        showDivider = index < items.lastIndex,
                    )
                }
            }
        }
    }

    if (confirmClear) {
        KitDialog(
            title = stringResource(R.string.activity_clear_title),
            body = stringResource(R.string.activity_clear_body),
            confirmText = stringResource(R.string.activity_clear_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: (() -> Unit)?, showDivider: Boolean) {
    val colors = Kit.colors
    Column {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.iconBackground),
            ) {
                Icon(
                    painter = painterResource(item.category.icon()),
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Unread: a lime dot and a heavier title, so the ones that
                    // arrived since the last look are findable without colour.
                    if (!item.isRead) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.pop),
                        )
                    }
                    Text(
                        item.title,
                        style = Kit.type.bodyStrong.copy(fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.ExtraBold),
                    )
                }
                Text(item.body, style = Kit.type.caption, color = colors.muted)
                Text(relativeTime(item.shownAt), style = Kit.type.caption, color = colors.muted)
            }
            if (onClick != null) {
                Icon(
                    painter = painterResource(PamojaIcons.ChevronRight),
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line),
            )
        }
    }
}

private fun NotificationCategory.icon(): Int = when (this) {
    NotificationCategory.ACHIEVEMENT -> PamojaIcons.Trophy
    NotificationCategory.GROUP_ACTIVITY -> PamojaIcons.Users
    NotificationCategory.REMINDER -> PamojaIcons.Footprints
    NotificationCategory.RECAP -> PamojaIcons.Clock
}

/**
 * "12 min ago", at the coarsest unit that is still honest.
 *
 * Same rule as the last-synced label in Settings: nothing under a minute gets a
 * second count, because a number ticking once a second reads as something being
 * wrong rather than as precision.
 */
@Composable
private fun relativeTime(epochMillis: Long): String {
    val elapsed = System.currentTimeMillis() - epochMillis
    val minutes = elapsed / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> stringResource(R.string.activity_time_now)
        minutes < 60 -> stringResource(R.string.activity_time_minutes, minutes.toInt())
        hours < 24 -> stringResource(R.string.activity_time_hours, hours.toInt())
        else -> stringResource(R.string.activity_time_days, days.toInt())
    }
}
