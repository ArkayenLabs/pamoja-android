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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.toSnackbarMessage
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.delay
import java.util.Calendar

// PullToRefreshBox is still marked experimental in Material 3. It is the
// official pull-to-refresh and the alternative is hand-rolling the gesture,
// which would be worse and would still have to be replaced later.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onSettingsClick: () -> Unit,
    onActivityClick: () -> Unit,
    onSessionExpired: () -> Unit,
    onOpenInvite: (String) -> Unit,
    onConnectHealth: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    joinViewModel: CreateOrJoinViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val context = LocalContext.current
    val uiState     by viewModel.uiState.collectAsState()
    val joinUiState by joinViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showJoinDialog by remember { mutableStateOf(false) }
    var inviteLink     by remember { mutableStateOf("") }

    // Hoisted: shown from the scanner callback, which is not a composable scope.
    val qrNotPamojaMessage = stringResource(R.string.home_join_qr_not_pamoja)
    val qrFailedMessage    = stringResource(R.string.home_join_qr_failed)

    // Re-read on resume, not just at construction. Health Connect permission
    // can be granted or revoked in system settings while this screen is alive,
    // and returning from granting it should clear the prompt immediately.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshHealthConnectStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    // An invite captured before this person had an account. Opens the preview
    // rather than joining, so they see the group before they are in it.
    LaunchedEffect(joinUiState.pendingCode) {
        joinUiState.pendingCode?.let { code ->
            joinViewModel.clearPendingCode()
            onOpenInvite(code)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* The OS owns the answer; nothing here needs to react to it. */ }

    // The system dialog is no longer fired on arrival. It used to go up on the
    // first composition of Home, before the user had a group, any steps, or a
    // reason to say yes, and on Android 13+ that single denial is permanent.
    // Now the primer asks first and only "Turn on" spends the real prompt.
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
        snackbarHost   = { SnackbarHost(hostState = snackbarHostState) }
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
                    // Greeting header
                    item {
                        HomeHeader(
                            name = uiState.userName
                                .takeIf { it.isNotBlank() }
                                ?.split(" ")
                                ?.firstOrNull() ?: stringResource(R.string.home_greeting_fallback),
                            onSettingsClick = onSettingsClick,
                            onActivityClick = onActivityClick,
                            unreadCount = uiState.unreadActivityCount,
                        )
                    }

                    // Sits under the greeting rather than over the content, so
                    // it states a condition without hiding anything.
                    item {
                        OfflineBanner(
                            isOffline = uiState.isOffline,
                            lastUpdatedLabel = if (uiState.hasContent) {
                                stringResource(R.string.offline_home_stale)
                            } else null,
                        )
                    }

                    // Without the permission nothing on this screen can ever be
                    // anything but zero, so it is said here rather than left to
                    // be discovered in Settings.
                    if (uiState.needsHealthConnect) {
                        item {
                            PamojaNotice(
                                icon = PamojaIcons.Footprints,
                                title = stringResource(R.string.home_health_needed_title),
                                body = stringResource(R.string.home_health_needed_body),
                                tone = NoticeTone.Warning,
                                actionLabel = stringResource(R.string.home_health_needed_action),
                                onAction = onConnectHealth,
                                onDismiss = viewModel::dismissHealthConnectPrompt,
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
                                // The count sits with the label, per the design.
                                // It answers "is this all of them" without
                                // making the reader count rows.
                                Text(
                                    text  = "${uiState.groups.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                    }

                    // Group cards
                    items(uiState.groups) { group ->
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

                // ── Floating bottom action bar ────────────────────────────
                BottomActionBar(
                    onCreateGroup = onCreateGroup,
                    onJoinGroup   = { showJoinDialog = true },
                    modifier      = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────
@Composable
private fun HomeHeader(
    name: String,
    onSettingsClick: () -> Unit,
    onActivityClick: () -> Unit,
    unreadCount: Int,
) {
    val colors = LocalPamojaColors.current
    // Time-aware greeting. The hour is remembered, not the resolved string:
    // stringResource cannot be called inside remember, and resolving outside it
    // also means the greeting follows a language change.
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.home_good_morning
            hour < 17 -> R.string.home_good_afternoon
            else -> R.string.home_good_evening
        }
    )

    // Rotating motivational subtitles, cycles every 4 seconds
    val subtitles = listOf(
        stringResource(R.string.home_tagline_1),
        stringResource(R.string.home_tagline_2),
        stringResource(R.string.home_tagline_3),
        stringResource(R.string.home_tagline_4),
        stringResource(R.string.home_tagline_5),
    )
    var subtitleIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000)
            subtitleIndex = (subtitleIndex + 1) % subtitles.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6)
            .padding(top = Spacing.x4, bottom = Spacing.x1)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // headlineMedium, not headlineLarge, and clamped.
            //
            // The design draws this at its h1 size next to a single control, in
            // a frame where "Good morning, Ravi" just fits. This header carries
            // two controls, so on a 360dp phone the greeting gets about 224dp,
            // and at 30sp even a short first name wrapped. Two lines is the
            // fallback rather than the normal case now, and the name is capped
            // so an unusually long one cannot push it past that.
            Text(
                text  = "$greeting, $name",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Activity, with a dot when something arrived since the last look.
            // A dot rather than a count: the question people have is "did I
            // miss anything", and a number invites reading it as a to-do list.
            IconButton(
                onClick = onActivityClick,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        painter = painterResource(PamojaIcons.Bell),
                        contentDescription = stringResource(R.string.activity_open_desc),
                        tint = colors.accentPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                // Ringed in the page colour so the dot stays
                                // legible where it overlaps the bell.
                                .background(colors.surfaceApp)
                                .padding(1.dp)
                                .clip(CircleShape)
                                .background(colors.statusDanger)
                        )
                    }
                }
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(44.dp) // Touch target
            ) {
                Icon(
                    painter = painterResource(PamojaIcons.Settings),
                    contentDescription = stringResource(R.string.home_settings_desc),
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.x2))
        // The design puts the tagline in the brand colour at semibold, which is
        // what stops it reading as a second, greyer heading.
        Text(
            text  = subtitles[subtitleIndex],
            style = MaterialTheme.typography.titleSmall,
            color = colors.accentPrimary
        )
    }
}

// ─── Join via link card ───────────────────────────────────────────────────────
@Composable
private fun JoinLinkCard(onClick: () -> Unit) {
    val colors = LocalPamojaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6, vertical = Spacing.x3)
            .clip(RoundedCornerShape(PamojaRadii.md))
            .background(colors.accentTealSubtle)
            .clickable { onClick() }
            .padding(horizontal = Spacing.x4, vertical = Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.accentTeal.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(PamojaIcons.Link),
                contentDescription = null,
                tint     = colors.accentTeal,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text     = stringResource(R.string.home_join_cta),
            style    = MaterialTheme.typography.bodyMedium,
            color    = colors.accentTeal,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(PamojaIcons.ChevronRight),
            contentDescription = null,
            tint     = colors.accentTeal,
            modifier = Modifier.size(16.dp)
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
            if (WeekWindow.isCurrent(group.weekStart, group.startDay) && group.weeklyTarget > 0) {
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
private fun BottomActionBar(
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPamojaColors.current
    // Gradient scrim behind the buttons so they're never transparent over content
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to colors.surfaceApp.copy(alpha = 0f),
                        0.25f to colors.surfaceApp
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.x6, vertical = Spacing.x4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3)
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
