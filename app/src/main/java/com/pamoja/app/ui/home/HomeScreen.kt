package com.pamoja.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.domain.model.Group
import com.pamoja.app.ui.CreateOrJoinViewModel
import com.pamoja.app.domain.error.AppError
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.GroupListSkeleton
import com.pamoja.app.ui.components.NotificationPrimerDialog
import com.pamoja.app.ui.components.GroupAvatar
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.formatSyncTime
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaBottomBar
import com.pamoja.app.ui.components.PamojaMainTab
import com.pamoja.app.ui.components.toSnackbarMessage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.ui.theme.Layout
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.util.InviteLink
import com.pamoja.app.util.QrScanner
import kotlinx.coroutines.launch
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

// PullToRefreshBox is still marked experimental in Material 3. It is the
// official pull-to-refresh and the alternative is hand-rolling the gesture,
// which would be worse and would still have to be replaced later.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onTodayClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSessionExpired: () -> Unit,
    onOpenInvite: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    joinViewModel: CreateOrJoinViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val context = LocalContext.current
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val joinUiState by joinViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showJoinDialog by remember { mutableStateOf(false) }
    var inviteLink     by remember { mutableStateOf("") }

    // Hoisted: shown from the scanner callback, which is not a composable scope.
    val qrNotPamojaMessage = stringResource(R.string.home_join_qr_not_pamoja)
    val qrFailedMessage    = stringResource(R.string.home_join_qr_failed)

    // Null until a sync has ever happened, which renders as no timestamp.
    val homeSyncedAt = formatSyncTime(context, uiState.lastSyncedAt)

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect

        // Matched on type rather than by searching the message for "Session
        // expired", which broke the moment any wording changed.
        if (error is AppError.SessionExpired) {
            viewModel.clearError()
            onSessionExpired()
            return@LaunchedEffect
        }

        // Only a passing note when there is still content behind it. With
        // nothing to show, the full error state renders instead and a snackbar
        // on top of it would be saying the same thing twice.
        if (uiState.showErrorSnackbar) {
            snackbarHostState.showSnackbar(error.toSnackbarMessage(context))
            viewModel.clearError()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* The OS owns the answer; nothing here needs to react to it. */ }

    // The system dialog is no longer fired on arrival. It used to go up on the
    // first composition of Home, before the user had a group, any steps, or a
    // reason to say yes, and on Android 13+ that single denial is permanent.
    // Now the primer asks first and only the allow action spends the real prompt.
    if (uiState.showNotificationPrimer &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        NotificationPrimerDialog(
            onAllow = {
                viewModel.onNotificationPrimerAnswered()
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { viewModel.onNotificationPrimerAnswered() },
        )
    }

    // ── Join-via-link dialog ──────────────────────────────────────────────────
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor   = colors.surface3,
            shape            = RoundedCornerShape(PamojaRadii.xl),
            title = {
                Text(
                    text  = stringResource(R.string.home_join_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text  = stringResource(R.string.home_join_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(Spacing.x4))
                    OutlinedTextField(
                        value         = inviteLink,
                        onValueChange = { inviteLink = it },
                        placeholder   = {
                            Text(
                                text  = stringResource(R.string.home_join_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textTertiary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(PamojaRadii.sm),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = colors.accentPrimary,
                            unfocusedBorderColor    = colors.borderDefault,
                            focusedContainerColor   = colors.surfaceInput,
                            unfocusedContainerColor = colors.surfaceInput,
                            cursorColor             = colors.accentPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(Spacing.x3))

                    // The in-person path. Someone standing next to you shows
                    // their invite screen and this reads it, instead of them
                    // dictating a URL.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(PamojaRadii.sm))
                            .clickable {
                                QrScanner.scan(context) { result ->
                                    result.onSuccess { raw ->
                                        val code = joinViewModel.normalise(raw)
                                        if (InviteLink.isInviteLink(raw) || code.isNotBlank()) {
                                            showJoinDialog = false
                                            inviteLink = ""
                                            onOpenInvite(code)
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(qrNotPamojaMessage)
                                            }
                                        }
                                    }
                                    // A cancelled scan is a choice, so it says
                                    // nothing and leaves the dialog as it was.
                                    result.onFailure { e ->
                                        if (e !is QrScanner.Cancelled) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(qrFailedMessage)
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(vertical = Spacing.x3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(PamojaIcons.QrCode),
                            contentDescription = null,
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.x2))
                        Text(
                            text  = stringResource(R.string.home_join_scan),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentPrimary,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Opens the preview instead of joining. The dialog has no
                        // room to explain a full group or a dead code, and the
                        // preview handles all of it in one place.
                        val code = joinViewModel.normalise(inviteLink)
                        showJoinDialog = false
                        inviteLink = ""
                        onOpenInvite(code)
                    },
                    enabled = inviteLink.isNotBlank(),
                    shape   = RoundedCornerShape(PamojaRadii.sm),
                    colors  = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text(stringResource(R.string.common_continue), color = colors.textOnBrand, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = colors.surfaceApp,
        snackbarHost   = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.showErrorState) {
                // Nothing loaded, so the failure IS the screen.
                PamojaErrorState(
                    error = uiState.error!!,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                // Wraps the list rather than the whole Box, so the gesture only
                // exists where there is content to refresh and never fights the
                // full-screen error state above.
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Stable brand bar: no rotating copy or time-dependent greeting.
                    item {
                        GroupsHeader()
                    }

                    // Sits under the greeting rather than over the content, so
                    // it states a condition without hiding anything.
                    item {
                        OfflineBanner(
                            isOffline = uiState.isOffline,
                            lastUpdatedLabel = when {
                                !uiState.hasContent -> null
                                homeSyncedAt != null ->
                                    stringResource(R.string.offline_showing_from, homeSyncedAt)
                                else -> stringResource(R.string.offline_home_stale)
                            },
                        )
                    }

                    item {
                        GroupActions(
                            onCreateGroup = onCreateGroup,
                            onJoinGroup = { showJoinDialog = true },
                        )
                    }

                    // An invitation from an interrupted sign-in is remembered,
                    // but never forced into a later session. The person can see
                    // why it is here, continue deliberately, or dismiss it.
                    joinUiState.pendingCode?.let { code ->
                        item(key = "pending-invite-$code") {
                            PamojaNotice(
                                icon = PamojaIcons.Link,
                                title = stringResource(R.string.home_pending_invite_title),
                                body = stringResource(R.string.home_pending_invite_body),
                                tone = NoticeTone.Info,
                                actionLabel = stringResource(R.string.home_pending_invite_action),
                                onAction = {
                                    joinViewModel.markPendingInviteOpened()
                                    onOpenInvite(code)
                                },
                                onDismiss = joinViewModel::dismissPendingInvite,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.x6,
                                    vertical = Spacing.x2,
                                ),
                            )
                        }
                    }

                    // Skeleton matches the real card's shape, so nothing jumps
                    // when the groups arrive.
                    if (!uiState.hasLoadedOnce) {
                        item {
                            GroupListSkeleton(
                                modifier = Modifier.padding(
                                    horizontal = Spacing.x6,
                                    vertical = Spacing.x5,
                                )
                            )
                        }
                    }

                    // Section label
                    if (uiState.groups.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = Spacing.x6, end = Spacing.x6,
                                        top = Spacing.x5, bottom = Spacing.x2
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text  = stringResource(R.string.home_your_groups),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                    }

                    // Group cards
                    items(
                        items = uiState.groups,
                        key = Group::groupId,
                        contentType = { "group" },
                    ) { group ->
                        GroupCard(
                            group   = group,
                            onClick = { onGroupClick(group.groupId) }
                        )
                        Spacer(modifier = Modifier.height(Spacing.x2))
                    }

                    // Only once we know the list is genuinely empty, rather than
                    // still arriving. Otherwise this flashes before content.
                    if (uiState.showEmptyState) {
                        item {
                            EmptyGroupsState()
                        }
                    }

                    item { Spacer(modifier = Modifier.height(120.dp)) }
                }
                }

                PamojaBottomBar(
                    selected = PamojaMainTab.Groups,
                    onToday = onTodayClick,
                    onGroups = {},
                    onYou = onSettingsClick,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────
@Composable
private fun GroupsHeader(
) {
    val colors = LocalPamojaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6)
            .padding(top = Spacing.x3, bottom = Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.groups_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─── Group card ───────────────────────────────────────────────────────────────
@Composable
fun GroupCard(group: Group, onClick: () -> Unit) {
    val colors = LocalPamojaColors.current
    val cardShape = RoundedCornerShape(PamojaRadii.xl)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6)
            .clip(cardShape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, cardShape)
            .clickable { onClick() }
            .padding(Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3)
    ) {
        // The design's 54dp squircle. Larger and softer than a small rounded
        // square, which is what makes a list of groups scan as people rather
        // than as rows in a table.
        GroupAvatar(
            name = group.name,
            size = Layout.groupAvatar,
            photoUrl = group.photoUrl,
        )

        // Group info
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text     = group.name,
                style    = MaterialTheme.typography.titleMedium,
                color    = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Mono caps, per the design. It is a label for the number beside it
            // rather than a sentence, and setting it as one keeps it from
            // competing with the group's name directly above.
            Text(
                text  = stringResource(
                    R.string.home_group_goal, "%,d".format(group.weeklyTarget)
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = colors.textTertiary
            )

            // Only drawn once this week's cache has actually landed. A total
            // from last week under this week's goal would be worse than showing
            // nothing, so a stale or missing marker renders no bar at all rather
            // than a confident zero.
            if (WeekWindow.isCurrent(group) && group.weeklyTarget > 0) {
                val fraction = (group.weeklySteps.toFloat() / group.weeklyTarget)
                    .coerceIn(0f, 1f)

                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(PillShape)
                        .background(colors.surface2)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(PillShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(colors.accentAmber, colors.accentPrimary)
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                            )
                        ) {
                            append("%,d".format(group.weeklySteps))
                        }
                        append(" · ${(fraction * 100).toInt()}%")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                )
            }
        }

        // Chevron
        Icon(
            painter = painterResource(PamojaIcons.ChevronRight),
            contentDescription = stringResource(R.string.home_open_group_desc),
            tint     = colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────
@Composable
private fun EmptyGroupsState() {
    val colors = LocalPamojaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6, vertical = Spacing.x12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.x3)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(PamojaRadii.xl))
                .background(colors.accentPrimarySubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(PamojaIcons.Users),
                contentDescription = null,
                tint     = colors.accentPrimary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(Spacing.x1))
        Text(
            text  = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary
        )
        Text(
            text      = stringResource(R.string.home_empty_body),
            style     = MaterialTheme.typography.bodyMedium,
            color     = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Bottom action bar ────────────────────────────────────────────────────────
// Two equal buttons side by side, floating above nav bar.
@Composable
private fun GroupActions(
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6, vertical = Spacing.x4),
    ) {
        Text(
            text = stringResource(R.string.groups_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(Spacing.x4))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            // Join (tonal secondary)
            Button(
                onClick  = onJoinGroup,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape  = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimarySubtle,
                    contentColor   = colors.accentPrimary
                )
            ) {
                Icon(
                    painter = painterResource(PamojaIcons.Link),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.accentPrimary
                )
                Spacer(modifier = Modifier.width(Spacing.x2))
                Text(
                    text     = stringResource(R.string.home_join_short),
                    style    = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }

            // Create (primary)
            Button(
                onClick  = onCreateGroup,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape  = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor   = colors.textOnBrand
                )
            ) {
                Icon(
                    painter = painterResource(PamojaIcons.Add),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.textOnBrand
                )
                Spacer(modifier = Modifier.width(Spacing.x2))
                Text(
                    text     = stringResource(R.string.home_new_group),
                    style    = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}
