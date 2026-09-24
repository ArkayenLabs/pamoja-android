package com.pamoja.app.ui.home

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pamoja.app.R
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.ui.CreateOrJoinViewModel
import com.pamoja.app.ui.components.GroupAvatar
import com.pamoja.app.ui.components.NotificationPrimerDialog
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaBottomBar
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.PamojaMainTab
import com.pamoja.app.ui.components.PamojaMark
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.components.formatSyncTime
import com.pamoja.app.ui.components.toSnackbarMessage
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

/**
 * Pamoja's personal landing screen. Groups still provide the motivation, but
 * the first answer here is now the one a walker opens the app for: how did I do
 * this week, and what happened today?
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGroupClick: (String) -> Unit,
    onGroupsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onActivityClick: () -> Unit,
    onSessionExpired: () -> Unit,
    onOpenInvite: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    joinViewModel: CreateOrJoinViewModel = hiltViewModel(),
) {
    val colors = LocalPamojaColors.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val joinUiState by joinViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val syncedAt = formatSyncTime(context, uiState.lastSyncedAt)

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
        if (error is AppError.SessionExpired) {
            viewModel.clearError()
            onSessionExpired()
        } else if (uiState.showErrorSnackbar) {
            snackbarHostState.showSnackbar(error.toSnackbarMessage(context))
            viewModel.clearError()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    if (
        uiState.showNotificationPrimer &&
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
            onDismiss = viewModel::onNotificationPrimerAnswered,
        )
    }

    val healthPermissionDenied = stringResource(R.string.home_health_permission_denied)
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val hasPermission = granted.containsAll(HealthConnectReader.REQUIRED_PERMISSIONS)
        viewModel.onHealthConnectPermissionResult(hasPermission)
        if (!hasPermission) {
            scope.launch { snackbarHostState.showSnackbar(healthPermissionDenied) }
        }
    }

    Scaffold(
        containerColor = colors.surfaceApp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.showErrorState) {
                PamojaErrorState(
                    error = requireNotNull(uiState.error),
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 116.dp),
                    ) {
                        item {
                            DashboardHeader(
                                days = uiState.personalDays,
                                unreadActivityCount = uiState.unreadActivityCount,
                                onActivityClick = onActivityClick,
                            )
                        }

                        item {
                            OfflineBanner(
                                isOffline = uiState.isOffline,
                                lastUpdatedLabel = syncedAt?.let {
                                    stringResource(R.string.offline_showing_from, it)
                                },
                            )
                        }

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
                                        horizontal = Spacing.x5,
                                        vertical = Spacing.x2,
                                    ),
                                )
                            }
                        }

                        if (uiState.needsHealthConnect) {
                            item {
                                HealthConnectBanner(
                                    onConnect = {
                                        viewModel.onHealthConnectPermissionRequested()
                                        healthPermissionLauncher.launch(
                                            HealthConnectReader.REQUIRED_PERMISSIONS
                                        )
                                    },
                                    onDismiss = viewModel::dismissHealthConnectPrompt,
                                    modifier = Modifier.padding(
                                        horizontal = Spacing.x5,
                                        vertical = Spacing.x2,
                                    ),
                                )
                            }
                        }

                        item {
                            if (uiState.hasLoadedPersonalSteps) {
                                WeeklyOverview(
                                    days = uiState.personalDays,
                                    weeklySteps = uiState.personalWeekSteps,
                                    todaySteps = uiState.todaySteps,
                                    syncedAt = syncedAt,
                                )
                            } else {
                                DashboardSkeleton()
                            }
                        }

                        item {
                            if (uiState.groups.isNotEmpty()) {
                                GroupProgress(
                                    groups = uiState.groups,
                                    onOpenGroup = onGroupClick,
                                    onOpenAll = onGroupsClick,
                                )
                            } else if (uiState.hasLoadedOnce) {
                                NoGroupPreview(onOpenGroups = onGroupsClick)
                            }
                        }
                    }
                }

                PamojaBottomBar(
                    selected = PamojaMainTab.Today,
                    onToday = {},
                    onGroups = onGroupsClick,
                    onYou = onSettingsClick,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun HealthConnectBanner(
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.statusWarningSubtle,
        shape = RoundedCornerShape(PamojaRadii.md),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.x2, vertical = Spacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(PamojaRadii.sm))
                    .background(colors.accentAmber.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(PamojaIcons.Footprints),
                    contentDescription = null,
                    tint = colors.accentAmber,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.x3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_health_compact_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.dashboard_health_compact_body),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 2,
                )
            }
            TextButton(onClick = onConnect) {
                Text(
                    text = stringResource(R.string.home_health_needed_action),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentAmber,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(PamojaIcons.Close),
                    contentDescription = stringResource(R.string.common_dismiss),
                    tint = colors.textTertiary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
internal fun DashboardHeader(
    days: List<PersonalDay>,
    unreadActivityCount: Int,
    onActivityClick: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x5, vertical = Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PamojaMark(size = 34.dp)
        Spacer(modifier = Modifier.width(Spacing.x2))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = personalWeekLabel(days),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(Spacing.x3))
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(
                onClick = onActivityClick,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    painter = painterResource(PamojaIcons.Bell),
                    contentDescription = stringResource(R.string.activity_open_desc),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (unreadActivityCount > 0) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.accentPrimary)
                        .border(2.dp, colors.surfaceApp, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun WeeklyOverview(
    days: List<PersonalDay>,
    weeklySteps: Long,
    todaySteps: Long,
    syncedAt: String?,
) {
    val colors = LocalPamojaColors.current
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x5)
            .padding(top = Spacing.x6),
    ) {
        Text(
            text = stringResource(R.string.dashboard_your_week),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )

        Spacer(modifier = Modifier.height(Spacing.x5))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%,d".format(weeklySteps),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.dashboard_steps_this_week),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(Spacing.x3))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(PamojaIcons.TrendingUp),
                        contentDescription = null,
                        tint = colors.accentTeal,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                    Text(
                        text = stringResource(
                            if (weeklySteps > 0L) R.string.dashboard_week_momentum
                            else R.string.dashboard_week_start
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(112.dp)
                    .background(colors.borderDefault),
            )
            Spacer(modifier = Modifier.width(Spacing.x4))
            Column(modifier = Modifier.width(104.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accentPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.x2))
                Text(
                    text = "%,d".format(todaySteps),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.accentPrimary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.dashboard_steps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(Spacing.x4))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accentTeal),
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                    Text(
                        text = syncedAt?.let {
                            stringResource(R.string.dashboard_synced_at, it)
                        } ?: stringResource(R.string.dashboard_not_synced),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.x8))
        WeekBars(days = days, today = today)
        Spacer(modifier = Modifier.height(Spacing.x7))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderSubtle),
        )
    }
}

@Composable
private fun WeekBars(days: List<PersonalDay>, today: LocalDate) {
    val colors = LocalPamojaColors.current
    val maxSteps = max(1L, days.maxOfOrNull(PersonalDay::steps) ?: 1L)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val isToday = day.date == today
            val isFuture = day.date.isAfter(today)
            val fraction = (day.steps.toFloat() / maxSteps.toFloat()).coerceIn(0f, 1f)
            val barHeight = when {
                isFuture -> 30.dp
                day.steps <= 0L -> 10.dp
                else -> (34 + 58 * fraction).dp
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(38.dp),
            ) {
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        letterSpacing = 0.7.sp,
                    ),
                    color = if (isToday) colors.accentPrimary else colors.textTertiary,
                )
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isToday) colors.accentPrimary else colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(Spacing.x2))
                Box(
                    modifier = Modifier
                        .height(96.dp)
                        .width(22.dp)
                        .clip(PillShape)
                        .background(
                            if (isToday) colors.accentPrimarySubtle else colors.surface2,
                        )
                        .border(1.dp, colors.borderDefault, PillShape),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .height(barHeight)
                            .width(if (isToday) 13.dp else 12.dp)
                            .clip(PillShape)
                            .background(
                                when {
                                    isToday -> colors.accentPrimary
                                    isFuture -> colors.borderStrong
                                    else -> colors.accentTeal
                                }
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.x2))
                Text(
                    text = if (isFuture) "–" else compactSteps(day.steps),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isToday) colors.accentPrimary else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun GroupProgress(
    groups: List<Group>,
    onOpenGroup: (String) -> Unit,
    onOpenAll: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedGroup = groups.firstOrNull { it.groupId == selectedGroupId } ?: groups.first()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.x5, end = Spacing.x3, top = Spacing.x7),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_group_progress),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            if (groups.size > 1) {
                TextButton(onClick = onOpenAll) {
                    Text(
                        text = stringResource(R.string.dashboard_all_groups, groups.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentPrimary,
                    )
                }
            }
        }

        if (groups.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.x5),
                horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
            ) {
                items(groups, key = Group::groupId) { group ->
                    val selected = group.groupId == selectedGroup.groupId
                    Surface(
                        onClick = { selectedGroupId = group.groupId },
                        shape = PillShape,
                        color = if (selected) colors.accentPrimarySubtle else colors.surface1,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) colors.accentPrimary else colors.borderDefault,
                        ),
                    ) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) colors.accentPrimary else colors.textSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = Spacing.x3, vertical = Spacing.x2),
                        )
                    }
                }
            }
        }

        FeaturedGroup(
            group = selectedGroup,
            onOpen = { onOpenGroup(selectedGroup.groupId) },
        )
    }
}

@Composable
private fun FeaturedGroup(group: Group, onOpen: () -> Unit) {
    val colors = LocalPamojaColors.current
    val hasCurrentProgress = WeekWindow.isCurrent(group)
    val steps = if (hasCurrentProgress) group.weeklySteps else 0L
    val fraction = if (group.weeklyTarget > 0) {
        (steps.toFloat() / group.weeklyTarget).coerceIn(0f, 1f)
    } else {
        0f
    }
    val daysLeft = WeekWindow.daysLeftIn(group)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = Spacing.x5, vertical = Spacing.x5),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupAvatar(
                name = group.name,
                photoUrl = group.photoUrl,
                size = 48.dp,
            )
            Spacer(modifier = Modifier.width(Spacing.x3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildAnnotatedString {
                        append(pluralStringResource(
                            R.plurals.member_count,
                            group.memberCount,
                            group.memberCount,
                        ))
                        append("  ·  ")
                        append(weekDayRange(group))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.x5))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.accentPrimary, fontWeight = FontWeight.Bold)) {
                    append("%,d".format(steps))
                }
                append(" / %,d ".format(group.weeklyTarget))
                append(stringResource(R.string.dashboard_steps))
            },
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(Spacing.x3))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(PillShape)
                .background(colors.surface2),
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
                    ),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.x3))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.group_days_left, daysLeft, daysLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.dashboard_see_group),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentPrimary,
            )
            Spacer(modifier = Modifier.width(Spacing.x2))
            Icon(
                painter = painterResource(PamojaIcons.ChevronRight),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun NoGroupPreview(onOpenGroups: () -> Unit) {
    val colors = LocalPamojaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x5, vertical = Spacing.x7)
            .clip(RoundedCornerShape(PamojaRadii.xl))
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.xl))
            .clickable(onClick = onOpenGroups)
            .padding(Spacing.x5),
    ) {
        Text(
            text = stringResource(R.string.dashboard_no_group_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.x2))
        Text(
            text = stringResource(R.string.dashboard_no_group_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(Spacing.x4))
        Text(
            text = stringResource(R.string.dashboard_open_groups),
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentPrimary,
        )
    }
}

@Composable
private fun DashboardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x5, vertical = Spacing.x7),
    ) {
        SkeletonBlock(modifier = Modifier.width(96.dp), height = 12.dp)
        Spacer(modifier = Modifier.height(Spacing.x6))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.65f), height = 56.dp)
        Spacer(modifier = Modifier.height(Spacing.x3))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.45f), height = 18.dp)
        Spacer(modifier = Modifier.height(Spacing.x8))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 132.dp)
    }
}

private fun compactSteps(steps: Long): String = when {
    steps >= 1_000L -> "%.1fK".format(steps / 1_000f)
    else -> steps.toString()
}

private fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}".uppercase()
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "P"
    }
}

private fun weekDayRange(group: Group): String {
    val start = group.startDay.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val end = group.startDay.plus(6).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$start–$end"
}

@Composable
private fun personalWeekLabel(days: List<PersonalDay>): String {
    if (days.size != 7) return stringResource(R.string.dashboard_this_week)
    val start = days.first().date
    val end = days.last().date
    val sameMonth = start.month == end.month
    val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val endPattern = if (sameMonth) DateTimeFormatter.ofPattern("d", Locale.getDefault())
    else monthDay
    return "${start.format(monthDay)}–${end.format(endPattern)}"
}
