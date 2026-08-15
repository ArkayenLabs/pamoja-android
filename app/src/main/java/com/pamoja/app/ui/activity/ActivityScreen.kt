package com.pamoja.app.ui.activity

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.domain.model.ActivityItem
import com.pamoja.app.domain.model.NotificationCategory
import com.pamoja.app.ui.components.PamojaConfirmDialog
import com.pamoja.app.ui.components.PamojaEmptyState
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

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
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    // Opening the list is what marks it seen.
    LaunchedEffect(Unit) { viewModel.onOpened() }

    if (showClearConfirm) {
        PamojaConfirmDialog(
            title = stringResource(R.string.activity_clear_title),
            body = stringResource(R.string.activity_clear_body),
            confirmLabel = stringResource(R.string.activity_clear_confirm),
            onConfirm = {
                viewModel.clearAll()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.x4, vertical = Spacing.x2),
                verticalAlignment = Alignment.CenterVertically,
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
                    text = stringResource(R.string.activity_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                // Only offered when there is something to clear.
                if (uiState.items.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text(
                            text = stringResource(R.string.activity_clear),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> Column(
                    modifier = Modifier.padding(horizontal = Spacing.x6),
                ) {
                    repeat(5) {
                        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 64.dp)
                        Spacer(modifier = Modifier.height(Spacing.x3))
                    }
                }

                uiState.isEmpty -> PamojaEmptyState(
                    icon = PamojaIcons.Bell,
                    title = stringResource(R.string.activity_empty_title),
                    body = stringResource(R.string.activity_empty_body),
                    modifier = Modifier.padding(top = Spacing.x10),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.x6,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.x3),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        ActivityRow(
                            item = item,
                            onClick = item.groupId?.let { { onOpenGroup(it) } },
                        )
                    }

                    item {
                        Spacer(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .height(Spacing.x8)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    item: ActivityItem,
    onClick: (() -> Unit)?,
) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.lg)
    val accent = item.category.accentColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            // Unread sits on a lifted surface with a brand border, so the ones
            // that arrived since the last look are findable without reading
            // every row. Colour alone would not carry it, hence the border too.
            .background(if (item.isRead) colors.surface1 else colors.accentPrimarySubtle)
            .border(
                width = 1.dp,
                color = if (item.isRead) colors.borderSubtle else colors.accentPrimary,
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Spacing.x4),
        verticalAlignment = Alignment.Top,
    ) {
        // The design's icon chip: 40dp, softly rounded, filled with the
        // category's own tint. Bigger and rounder than a small square, which is
        // what stops a list of these reading as a settings menu.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(PamojaRadii.md))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.category.icon()),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(Spacing.x3))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.x2))
            // Mono caps, per the design. A timestamp is metadata, and setting it
            // as small caps stops it competing with the sentence above it.
            Text(
                text = relativeTime(item.shownAt).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
        }

        if (onClick != null) {
            Icon(
                painter = painterResource(PamojaIcons.ChevronRight),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun NotificationCategory.icon(): Int = when (this) {
    NotificationCategory.ACHIEVEMENT -> PamojaIcons.Trophy
    NotificationCategory.GROUP_ACTIVITY -> PamojaIcons.Users
    NotificationCategory.REMINDER -> PamojaIcons.Footprints
    NotificationCategory.RECAP -> PamojaIcons.Clock
}

@Composable
private fun NotificationCategory.accentColor() = when (this) {
    NotificationCategory.ACHIEVEMENT -> LocalPamojaColors.current.statusSuccess
    NotificationCategory.GROUP_ACTIVITY -> LocalPamojaColors.current.accentPrimary
    NotificationCategory.REMINDER -> LocalPamojaColors.current.statusWarning
    NotificationCategory.RECAP -> LocalPamojaColors.current.textSecondary
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
