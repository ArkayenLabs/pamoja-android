package com.pamoja.app.ui.home

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.SignalKind
import com.pamoja.app.domain.model.WalkedDays
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.model.WeeklyPace
import com.pamoja.app.ui.CreateOrJoinViewModel
import com.pamoja.app.ui.components.formatSyncTime
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.components.toSnackbarMessage
import com.pamoja.app.ui.group.GroupUiState
import com.pamoja.app.ui.group.GroupViewModel
import com.pamoja.app.ui.group.MemberStepData
import com.pamoja.app.ui.kit.ActionRow
import com.pamoja.app.ui.kit.Avatar
import com.pamoja.app.ui.kit.ButtonSize
import com.pamoja.app.ui.kit.CardHeader
import com.pamoja.app.ui.kit.CardTone
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.DayDots
import com.pamoja.app.ui.kit.GoalBar
import com.pamoja.app.ui.kit.GroupTile
import com.pamoja.app.ui.kit.HeroShape
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitDialog
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSheet
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.LightKitColors
import com.pamoja.app.ui.kit.PersonRow
import com.pamoja.app.ui.kit.PopChip
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.mutedContentColor
import com.pamoja.app.ui.theme.PamojaIcons
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home is your group (REBUILD_PLAN.md §6): the week's total, who walked
 * today, your own day, and the standings. Other groups are one tap away in
 * the switcher; group details and your settings sit top right. No tabs.
 *
 * Two ViewModels, each doing what it already did: [HomeViewModel] owns the
 * list of groups, which one is shown, and your own steps; [GroupViewModel]
 * owns the shown group's members and figures, from listeners that answer
 * from the local cache first.
 */
@Composable
fun HomeScreen(
    /** Set by group details after leaving or deleting a group. */
    goneGroupId: String?,
    onGoneGroupHandled: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onInvite: (String) -> Unit,
    onStartGroup: () -> Unit,
    onJoinWithLink: () -> Unit,
    onOpenInvite: (String) -> Unit,
    onSessionExpired: () -> Unit,
    onOpenPaywall: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    groupViewModel: GroupViewModel = hiltViewModel(),
    joinViewModel: CreateOrJoinViewModel = hiltViewModel(),
    signalsViewModel: GroupSignalsViewModel = hiltViewModel(),
) {
    val home by viewModel.uiState.collectAsStateWithLifecycle()
    val groupState by groupViewModel.uiState.collectAsStateWithLifecycle()
    val signals by signalsViewModel.uiState.collectAsStateWithLifecycle()
    val pending by joinViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val selectedId = home.selectedGroupId
    LaunchedEffect(selectedId) {
        selectedId?.let {
            groupViewModel.loadGroup(it)
            signalsViewModel.load(it)
        }
    }
    val currentSelectedId by rememberUpdatedState(selectedId)

    LaunchedEffect(goneGroupId) {
        goneGroupId?.let {
            viewModel.onSelectedGroupGone(it)
            onGoneGroupHandled()
        }
    }

    // Only for the group on screen: a stale "unavailable" about the previous
    // group must not push this one aside.
    LaunchedEffect(groupState.isGroupUnavailable, groupState.groupId) {
        if (groupState.isGroupUnavailable && groupState.groupId == selectedId) {
            viewModel.onSelectedGroupGone(groupState.groupId)
        }
    }

    LaunchedEffect(home.error) {
        val error = home.error ?: return@LaunchedEffect
        if (error is AppError.SessionExpired) {
            viewModel.clearError()
            onSessionExpired()
        } else if (home.showErrorSnackbar) {
            snackbar.showSnackbar(error.toSnackbarMessage(context))
            viewModel.clearError()
        }
    }
    LaunchedEffect(groupState.actionError) {
        groupState.actionError?.let {
            snackbar.showSnackbar(it.toSnackbarMessage(context))
            groupViewModel.clearActionError()
        }
    }
    LaunchedEffect(signals.error) {
        signals.error?.let {
            snackbar.showSnackbar(it.toSnackbarMessage(context))
            signalsViewModel.clearError()
        }
    }

    // A first-run invite (someone who signed up from a link and has no group
    // yet) opens its preview straight away. With groups already, it waits
    // below as a choice rather than taking over the screen.
    LaunchedEffect(pending.pendingCode, home.hasLoadedOnce, home.groups.isEmpty()) {
        val code = pending.pendingCode ?: return@LaunchedEffect
        if (home.hasLoadedOnce && home.groups.isEmpty()) {
            joinViewModel.markPendingInviteOpened()
            onOpenInvite(code)
        }
    }

    // Health Connect can be granted or revoked in system settings while the
    // app is away, and nothing says so. Resume is the only reliable signal.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHealthConnectStatus()
                groupViewModel.refreshHealthConnectStatus()
                // Only reloads if the day has turned since: cheers are per day.
                currentSelectedId?.let(signalsViewModel::load)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val healthDenied = stringResource(R.string.home_health_permission_denied)
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val hasPermission = granted.containsAll(HealthConnectReader.REQUIRED_PERMISSIONS)
        viewModel.onHealthConnectPermissionResult(hasPermission)
        if (!hasPermission) scope.launch { snackbar.showSnackbar(healthDenied) }
    }
    val connectSteps = {
        viewModel.onHealthConnectPermissionRequested()
        healthLauncher.launch(HealthConnectReader.REQUIRED_PERMISSIONS)
    }

    NotificationPrimer(show = signals.showNotificationPrimer, onAnswered = signalsViewModel::onNotificationPrimerAnswered)

    // The end of the group's preview, told once and only to its admin: never
    // to someone who joined through an invite (REBUILD_PLAN.md Phase 6).
    val endedPreviewGroup = groupState.group?.takeIf {
        groupState.groupAccess is com.pamoja.app.domain.model.GroupAccessState.PreviewExpired &&
            groupState.isAdmin &&
            it.groupId == selectedId &&
            home.previewEndSeen?.contains(it.groupId) == false
    }
    endedPreviewGroup?.let { group ->
        PreviewEnded(
            groupName = group.name,
            onSeePremium = {
                viewModel.onPreviewEndAnswered(group.groupId)
                onOpenPaywall(group.groupId)
            },
            onDismiss = { viewModel.onPreviewEndAnswered(group.groupId) },
        )
    }

    if (groupState.showGoalCelebration) {
        GoalCelebration(
            steps = groupState.combinedWeeklySteps,
            target = groupState.group?.weeklyTarget ?: 0,
            onDismiss = groupViewModel::dismissGoalCelebration,
        )
    }

    HomeContent(
        home = home,
        groupState = groupState,
        pendingCode = pending.pendingCode,
        snackbar = snackbar,
        onRefresh = {
            viewModel.refresh()
            groupViewModel.refresh()
        },
        onRetryHome = viewModel::retry,
        onRetryGroup = groupViewModel::retry,
        onSelectGroup = viewModel::selectGroup,
        onOpenDetails = onOpenDetails,
        onOpenSettings = onOpenSettings,
        onInvite = onInvite,
        onStartGroup = onStartGroup,
        onJoinWithLink = onJoinWithLink,
        onOpenPendingInvite = { code ->
            joinViewModel.markPendingInviteOpened()
            onOpenInvite(code)
        },
        onDismissPendingInvite = joinViewModel::dismissPendingInvite,
        onConnectSteps = connectSteps,
        signals = signals,
        onCheer = signalsViewModel::cheer,
        onNudge = signalsViewModel::nudge,
    )
}

/** Stateless rendering boundary, used by the debug preview and by tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    home: HomeUiState,
    groupState: GroupUiState,
    pendingCode: String?,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onRetryHome: () -> Unit,
    onRetryGroup: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onInvite: (String) -> Unit,
    onStartGroup: () -> Unit,
    onJoinWithLink: () -> Unit,
    onOpenPendingInvite: (String) -> Unit,
    onDismissPendingInvite: () -> Unit,
    onConnectSteps: () -> Unit,
    signals: SignalsUiState = SignalsUiState(),
    onCheer: (List<String>) -> Unit = {},
    onNudge: (List<String>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showSwitcher by rememberSaveable { mutableStateOf(false) }
    val selectedId = home.selectedGroupId

    // The shown group's name comes from its own document when it is here, so
    // a group opened from a link or a notification is named before the list
    // of groups has caught up.
    val shownGroup = groupState.group?.takeIf { it.groupId == selectedId }
        ?: home.groups.firstOrNull { it.groupId == selectedId }
    val inviteInactive = stringResource(R.string.group_invite_inactive)

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = home.isRefreshing || groupState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            KitScreen(
                gutter = 20.dp,
                spacing = 16.dp,
                top = {
                    HomeTopBar(
                        groupName = shownGroup?.name,
                        onSwitch = { showSwitcher = true },
                        onDetails = selectedId?.let { id -> { onOpenDetails(id) } },
                        userName = home.userName,
                        photoUrl = home.userPhotoUrl,
                        seed = home.userId.ifBlank { home.userName },
                        onProfile = onOpenSettings,
                    )
                },
            ) {
                when {
                    home.showErrorState -> LoadFailed(requireNotNull(home.error), onRetry = onRetryHome)

                    selectedId == null && home.hasLoadedOnce && home.groups.isEmpty() ->
                        NoGroup(onStartGroup = onStartGroup, onJoinWithLink = onJoinWithLink)

                    groupState.fatalError != null && !groupState.hasContent && !groupState.isGroupUnavailable ->
                        LoadFailed(requireNotNull(groupState.fatalError), onRetry = onRetryGroup)

                    selectedId == null || groupState.groupId != selectedId || shownGroup == null ||
                        !groupState.hasLoadedMembers -> HomeSkeleton()

                    else -> GroupHome(
                        group = shownGroup,
                        state = groupState,
                        home = home,
                        pendingCode = pendingCode,
                        onOpenPendingInvite = onOpenPendingInvite,
                        onDismissPendingInvite = onDismissPendingInvite,
                        onConnectSteps = onConnectSteps,
                        // Never the last group's cheers under this one's name.
                        signals = signals.takeIf { it.groupId == shownGroup.groupId } ?: SignalsUiState(),
                        onCheer = onCheer,
                        onNudge = onNudge,
                        onInvite = {
                            if (!shownGroup.inviteLinkActive) {
                                scope.launch { snackbar.showSnackbar(inviteInactive) }
                            } else {
                                onInvite(shownGroup.groupId)
                            }
                        },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (showSwitcher) {
        GroupSwitcher(
            groups = home.groups,
            selectedId = selectedId,
            onDismiss = { showSwitcher = false },
            onSelect = onSelectGroup,
            onStartGroup = onStartGroup,
            onJoinWithLink = onJoinWithLink,
        )
    }
}

@Composable
private fun HomeTopBar(
    groupName: String?,
    onSwitch: () -> Unit,
    onDetails: (() -> Unit)?,
    userName: String,
    photoUrl: String?,
    seed: String,
    onProfile: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            if (groupName != null) {
                // The name is the label; TalkBack adds "double tap to switch group".
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            onClickLabel = stringResource(R.string.home_switch_group_action),
                            role = Role.Button,
                            onClick = onSwitch,
                        ),
                ) {
                    Text(
                        groupName,
                        style = Kit.type.title.copy(fontSize = 26.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        painter = painterResource(PamojaIcons.ChevronRight),
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(90f),
                    )
                }
            } else {
                Text(stringResource(R.string.app_name), style = Kit.type.title.copy(fontSize = 26.sp))
            }
        }
        onDetails?.let {
            CircleIconButton(PamojaIcons.Users, stringResource(R.string.home_group_details_desc), onClick = it)
        }
        val profileDescription = stringResource(R.string.home_profile_desc)
        Surface(
            onClick = onProfile,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = profileDescription },
        ) {
            Avatar(
                name = userName,
                photoUrl = photoUrl,
                seed = seed,
                size = 44.dp,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun GroupHome(
    group: Group,
    state: GroupUiState,
    home: HomeUiState,
    pendingCode: String?,
    onOpenPendingInvite: (String) -> Unit,
    onDismissPendingInvite: () -> Unit,
    onConnectSteps: () -> Unit,
    signals: SignalsUiState,
    onCheer: (List<String>) -> Unit,
    onNudge: (List<String>) -> Unit,
    onInvite: () -> Unit,
) {
    val context = LocalContext.current
    if (home.isOffline) {
        val syncedAt = formatSyncTime(context, home.lastSyncedAt)
        KitNotice(
            title = stringResource(R.string.offline_banner_title),
            body = syncedAt?.let { stringResource(R.string.offline_showing_from, it) }
                ?: stringResource(R.string.offline_group_stale),
        )
    }
    pendingCode?.let { code ->
        KitNotice(
            title = stringResource(R.string.home_pending_invite_title),
            body = stringResource(R.string.home_pending_invite_body),
            actions = {
                PrimaryButton(
                    stringResource(R.string.home_pending_invite_action),
                    onClick = { onOpenPendingInvite(code) },
                    size = ButtonSize.Small,
                )
                SecondaryButton(stringResource(R.string.common_not_now), onClick = onDismissPendingInvite, size = ButtonSize.Small)
            },
        )
    }

    WeekCard(group = group, steps = state.combinedWeeklySteps, stale = home.isOffline)
    TodayCard(
        members = state.memberStepData,
        currentUserId = state.currentUserId,
        hasNudged = { signals.hasSent(SignalKind.NUDGE, it) },
        onNudge = onNudge,
    )
    YouAndCheers(
        home = home,
        members = state.memberStepData,
        signals = signals,
        onConnect = onConnectSteps,
        onCheerBack = onCheer,
    )
    StandingsCard(
        group = group,
        members = state.memberStepData,
        currentUserId = state.currentUserId,
        hasCheered = { signals.hasSent(SignalKind.CHEER, it) },
        onCheer = { onCheer(listOf(it)) },
    )

    // A full group has no room, so no invite. Alone in it, inviting is the
    // one thing worth doing, so it becomes the main button.
    val isFull = group.maxMemberCap > 0 && group.memberCount >= group.maxMemberCap
    if (!isFull) {
        val label = stringResource(R.string.home_invite_to, group.name)
        if (state.memberStepData.size <= 1) {
            PrimaryButton(label, onClick = onInvite, icon = PamojaIcons.Add)
        } else {
            SecondaryButton(label, onClick = onInvite, icon = PamojaIcons.Add)
        }
    }
}

/** The one number that matters: the group's week, with how it is going. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekCard(group: Group, steps: Long, stale: Boolean) {
    val target = group.weeklyTarget.toLong()
    val today = WeekWindow.todayFor(group)
    val daysLeft = WeekWindow.daysLeftIn(group.startDay, today)
    val elapsed = (7 - daysLeft) + LocalTime.now().toSecondOfDay() / 86_400.0
    val pace = WeeklyPace.of(steps, target, group.startDay, elapsed, daysLeft)
    // Counts this week as soon as its goal is met: the finalizer will, and
    // the chip ticking up is the moment worth having.
    val streak = group.streakFor(WeekWindow.startOf(group.startDay, today)) +
        if (pace == WeeklyPace.Reached) 1 else 0
    val stepsText = "%,d".format(steps)
    val targetText = "%,d".format(target)

    KitCard(tone = CardTone.Hero, padding = 20.dp, spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_this_week),
                style = Kit.type.label.copy(fontSize = 15.sp),
                modifier = Modifier.weight(1f),
            )
            // One lime chip at most. "1 week in a row" says nothing, so the
            // streak shows from two.
            if (streak >= 2) {
                PopChip(pluralStringResource(R.plurals.home_streak_weeks, streak, streak), icon = PamojaIcons.Flame)
            } else if (pace == WeeklyPace.Reached) {
                PopChip(stringResource(R.string.home_goal_reached), icon = PamojaIcons.Check)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Shrinks rather than wraps: a step count split across two lines
            // stops reading as one number.
            BasicText(
                stepsText,
                style = Kit.type.bigNumber.copy(color = Kit.colors.onHero),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = Kit.type.bigNumber.fontSize),
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                stringResource(R.string.group_of_target, targetText),
                style = Kit.type.bodyStrong.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                color = mutedContentColor(),
                modifier = Modifier.alignByBaseline(),
            )
        }
        GoalBar(
            progress = if (target > 0) steps.toFloat() / target else 0f,
            description = stringResource(R.string.home_week_progress_desc, stepsText, targetText),
            onHero = true,
        )
        // Withheld offline: time keeps passing while steps cannot arrive, so
        // an offline pace drifts towards bad news about people still walking.
        if (!stale) paceLine(pace, daysLeft)?.let {
            Text(it, style = Kit.type.label, color = mutedContentColor())
        }
        if (group.shieldUsedLastWeek(WeekWindow.startOf(group.startDay, today))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(PamojaIcons.ShieldCheck), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.home_shield_used), style = Kit.type.label, color = mutedContentColor())
            }
        }
    }
}

@Composable
private fun paceLine(pace: WeeklyPace, daysLeft: Int): String? {
    val left = pluralStringResource(R.plurals.group_days_left, daysLeft, daysLeft)
    return when (pace) {
        WeeklyPace.Reached -> null
        is WeeklyPace.OnPace -> if (pace.isToday) {
            stringResource(R.string.home_pace_finish_today, left)
        } else {
            stringResource(
                R.string.home_pace_on_pace,
                left,
                pace.finishDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            )
        }
        is WeeklyPace.PerDay -> stringResource(R.string.home_pace_per_day, left, "%,d".format(pace.steps))
    }
}

/** Who has walked today. Faces first; greyed until they have. */
@Composable
private fun TodayCard(
    members: List<MemberStepData>,
    currentUserId: String,
    hasNudged: (String) -> Boolean,
    onNudge: (List<String>) -> Unit,
) {
    val walked = members.count { it.walkedToday }
    KitCard {
        CardHeader(
            title = stringResource(R.string.home_today),
            detail = pluralStringResource(R.plurals.home_walked_count, walked, walked, members.size),
        )
        // Spread across the card when everyone fits, as drawn (a pair or three
        // sit together instead of at opposite edges). A bigger group scrolls,
        // and the next face shows cut off at the edge so it reads as more to
        // see rather than as everyone.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fits = members.size * FaceSlot.value <= maxWidth.value
            Row(
                horizontalArrangement = when {
                    !fits -> Arrangement.spacedBy(8.dp)
                    members.size >= 4 -> Arrangement.SpaceBetween
                    else -> Arrangement.spacedBy(16.dp)
                },
                modifier = if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),
            ) {
                members.forEach { member -> Face(member, isYou = member.user.userId == currentUserId) }
            }
        }

        // Only once somebody has walked: at seven in the morning nobody has,
        // and a list of everyone is not a nudge, it is an alarm clock.
        val waiting = members.filter { it.user.userId != currentUserId && !it.walkedToday }
        if (waiting.isNotEmpty() && members.any { it.walkedToday }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Kit.colors.line),
            )
            val toNudge = waiting.map { it.user.userId }.filterNot(hasNudged)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    notWalkedYet(waiting.map { firstName(it.user.name) }),
                    style = Kit.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    stringResource(if (toNudge.isEmpty()) R.string.home_nudged else R.string.home_nudge),
                    onClick = { onNudge(toNudge) },
                    size = ButtonSize.Small,
                    enabled = toNudge.isNotEmpty(),
                    icon = if (toNudge.isEmpty()) PamojaIcons.Check else PamojaIcons.Bell,
                )
            }
        }
    }
}

@Composable
private fun notWalkedYet(names: List<String>): String = when (names.size) {
    1 -> stringResource(R.string.home_not_walked_one, names[0])
    2 -> stringResource(R.string.home_not_walked_two, names[0], names[1])
    else -> pluralStringResource(R.plurals.home_not_walked_many, names.size - 2, names[0], names[1], names.size - 2)
}

private fun firstName(name: String): String = name.trim().substringBefore(' ').ifBlank { name }

/** How wide one face and its name get in the Today row. */
private val FaceSlot = 52.dp

@Composable
private fun Face(member: MemberStepData, isYou: Boolean) {
    val name = if (isYou) stringResource(R.string.home_you) else member.user.name.trim().substringBefore(' ')
    val description = stringResource(
        if (member.walkedToday) R.string.home_face_walked_desc else R.string.home_face_not_walked_desc,
        member.user.name,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(FaceSlot)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Avatar(
            name = member.user.name,
            photoUrl = member.user.photoUrl,
            seed = member.user.userId,
            size = 46.dp,
            walked = member.walkedToday,
            dimmed = !member.walkedToday,
        )
        Text(
            name,
            style = Kit.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = Kit.colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Your own day, with the cheers you received today beside it as drawn, or
 * under it where two cards side by side would not fit (a narrow window, a
 * large font).
 */
@Composable
private fun YouAndCheers(
    home: HomeUiState,
    members: List<MemberStepData>,
    signals: SignalsUiState,
    onConnect: () -> Unit,
    onCheerBack: (List<String>) -> Unit,
) {
    // Only people still in the group; someone who left since is not shown.
    val senders = signals.cheersReceived
        .mapNotNull { cheer -> members.firstOrNull { it.user.userId == cheer.fromUserId } }
        .distinctBy { it.user.userId }
    val cheers: @Composable (Modifier) -> Unit = { modifier ->
        CheerCard(
            senders = senders,
            cheeredBack = { signals.hasSent(SignalKind.CHEER, it) },
            onCheerBack = onCheerBack,
            modifier = modifier,
        )
    }
    if (senders.isEmpty()) {
        YouTodayCard(home, onConnect)
        return
    }
    val showsYouToday = home.needsHealthConnect || home.hasLoadedPersonalSteps
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sideBySide = showsYouToday && !home.needsHealthConnect &&
            maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.3f
        if (sideBySide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                YouTodayCard(home, onConnect, Modifier.weight(1f).fillMaxHeight())
                cheers(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                YouTodayCard(home, onConnect)
                cheers(Modifier)
            }
        }
    }
}

/** Cheers received today, in lime: a moment from a person, with a way to answer. */
@Composable
private fun CheerCard(
    senders: List<MemberStepData>,
    cheeredBack: (String) -> Boolean,
    onCheerBack: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = senders.first()
    val name = firstName(first.user.name)
    val owed = senders.map { it.user.userId }.filterNot(cheeredBack)
    KitCard(modifier = modifier, tone = CardTone.Pop, padding = 14.dp, spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(name = first.user.name, photoUrl = first.user.photoUrl, seed = first.user.userId, size = 38.dp)
            Text(
                if (senders.size == 1) {
                    stringResource(R.string.home_cheer_from_one, name)
                } else {
                    pluralStringResource(R.plurals.home_cheer_from_many, senders.size - 1, name, senders.size - 1)
                },
                style = Kit.type.bodyStrong.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 19.sp),
            )
        }
        Spacer(Modifier.weight(1f, fill = false))
        // Ink on lime in both themes, as drawn: the card itself does not change with the theme.
        Surface(
            onClick = { onCheerBack(owed) },
            enabled = owed.isNotEmpty(),
            shape = RoundedCornerShape(50),
            color = LightKitColors.button,
            contentColor = LightKitColors.onButton,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    stringResource(if (owed.isEmpty()) R.string.home_cheered else R.string.home_cheer_back),
                    style = Kit.type.buttonSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Your own day against your usual one, or the way to start counting. */
@Composable
private fun YouTodayCard(home: HomeUiState, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    if (home.needsHealthConnect) {
        KitCard(modifier = modifier) {
            Text(stringResource(R.string.home_steps_title), style = Kit.type.title)
            Text(stringResource(R.string.home_connect_body), style = Kit.type.body, color = Kit.colors.muted)
            PrimaryButton(
                stringResource(R.string.home_connect_action),
                onClick = onConnect,
                size = ButtonSize.Small,
                icon = PamojaIcons.Footprints,
            )
        }
        return
    }
    if (!home.hasLoadedPersonalSteps) return
    val todayText = "%,d".format(home.todaySteps)
    KitCard(modifier = modifier, spacing = 8.dp) {
        Text(stringResource(R.string.home_you_today), style = Kit.type.caption.copy(fontWeight = FontWeight.Bold), color = Kit.colors.muted)
        Text(todayText, style = Kit.type.number)
        home.usualSteps?.let { usual ->
            val usualText = "%,d".format(usual)
            Text(stringResource(R.string.home_your_usual, usualText), style = Kit.type.caption, color = Kit.colors.muted)
            GoalBar(
                progress = home.todaySteps.toFloat() / usual,
                description = stringResource(R.string.home_today_vs_usual_desc, todayText, usualText),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * This week's standings, most steps first, with each member's days so far and
 * a heart to cheer them. A member not synced this week says so.
 */
@Composable
private fun StandingsCard(
    group: Group,
    members: List<MemberStepData>,
    currentUserId: String,
    hasCheered: (String) -> Boolean,
    onCheer: (String) -> Unit,
) {
    val ranked = remember(members) {
        members.sortedWith(
            compareByDescending<MemberStepData> { it.weekSynced }.thenByDescending { it.weeklySteps },
        )
    }
    val start = group.startDay
    val elapsed = 8 - WeekWindow.daysLeftIn(start, WeekWindow.todayFor(group))
    KitCard(spacing = 4.dp) {
        CardHeader(
            title = stringResource(R.string.home_this_week),
            detail = stringResource(
                R.string.home_week_range,
                start.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                start.plus(6).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            ),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ranked.forEach { member ->
            val isYou = member.user.userId == currentUserId
            val mask = member.walkedDays
            PersonRow(
                name = if (isYou) stringResource(R.string.home_you) else member.user.name,
                avatarName = member.user.name,
                photoUrl = member.user.photoUrl,
                seed = member.user.userId,
                emphasised = isYou,
                // No dots when unknown (not synced this week, or an older app
                // version): a row of empty days would read as missed ones.
                below = if (mask == null) null else {
                    {
                        DayDots(
                            days = List(7) { WalkedDays.walked(mask, it) },
                            elapsed = elapsed,
                            description = pluralStringResource(
                                R.plurals.home_days_walked_desc,
                                elapsed,
                                WalkedDays.count(mask and ((1 shl elapsed) - 1)),
                                elapsed,
                            ),
                        )
                    }
                },
            ) {
                if (member.weekSynced) {
                    Text(
                        "%,d".format(member.weeklySteps),
                        style = Kit.type.bodyStrong.copy(fontWeight = FontWeight.ExtraBold),
                    )
                } else {
                    Text(stringResource(R.string.home_not_synced), style = Kit.type.caption, color = Kit.colors.muted)
                }
                if (isYou) {
                    Spacer(Modifier.width(44.dp))
                } else {
                    CheerButton(member.user.name, cheered = hasCheered(member.user.userId)) {
                        onCheer(member.user.userId)
                    }
                }
            }
        }
    }
}

/** One cheer per person per day; the heart stays filled once sent. */
@Composable
private fun CheerButton(name: String, cheered: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (cheered) R.string.home_cheered_desc else R.string.home_cheer_desc, name)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = !cheered, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(if (cheered) PamojaIcons.HeartFilled else PamojaIcons.Heart),
            contentDescription = null,
            tint = Kit.colors.ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GroupSwitcher(
    groups: List<Group>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onStartGroup: () -> Unit,
    onJoinWithLink: () -> Unit,
) {
    KitSheet(onDismiss = onDismiss, title = stringResource(R.string.home_your_groups)) { close ->
        groups.forEach { group ->
            val selected = group.groupId == selectedId
            val members = pluralStringResource(R.plurals.member_count, group.memberCount, group.memberCount)
            // Only this week's total, never a stale one under this week's goal.
            val summary = if (WeekWindow.isCurrent(group)) {
                stringResource(
                    R.string.home_group_summary,
                    members,
                    "%,d".format(group.weeklySteps),
                    "%,d".format(group.weeklyTarget),
                )
            } else {
                members
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        close { onSelect(group.groupId) }
                    }
                    .padding(vertical = 8.dp),
            ) {
                GroupTile(photoUrl = group.photoUrl, size = 48.dp, highlighted = selected)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        group.name,
                        style = Kit.type.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(summary, style = Kit.type.caption, color = Kit.colors.muted)
                }
                if (selected) {
                    Icon(
                        painter = painterResource(PamojaIcons.Check),
                        contentDescription = stringResource(R.string.home_current_group_desc),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        if (groups.isNotEmpty()) {
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Kit.colors.line),
            )
        }
        ActionRow(stringResource(R.string.home_start_new_group), icon = PamojaIcons.Add, onClick = { close(onStartGroup) })
        ActionRow(stringResource(R.string.home_join_with_link), icon = PamojaIcons.Link, onClick = { close(onJoinWithLink) })
    }
}

@Composable
private fun NoGroup(onStartGroup: () -> Unit, onJoinWithLink: () -> Unit) {
    KitCard(tone = CardTone.Hero, padding = 24.dp, spacing = 16.dp) {
        GroupTile(photoUrl = null, size = 56.dp, highlighted = false)
        Text(stringResource(R.string.home_empty_title), style = Kit.type.headline)
        Text(stringResource(R.string.home_empty_body), style = Kit.type.body, color = mutedContentColor())
        Spacer(Modifier.height(4.dp))
        PrimaryButton(stringResource(R.string.home_start_group), onClick = onStartGroup)
        SecondaryButton(stringResource(R.string.home_join_with_link), onClick = onJoinWithLink, icon = PamojaIcons.Link)
    }
}

@Composable
private fun LoadFailed(error: AppError, onRetry: () -> Unit) {
    val context = LocalContext.current
    val copy = error.toErrorCopy()
    val retryLabel = copy.retryLabel
    KitNotice(
        title = stringResource(copy.title),
        body = copy.body(context),
        danger = true,
        // No button when retrying cannot help.
        actions = if (retryLabel != null) {
            { PrimaryButton(stringResource(retryLabel), onClick = onRetry, size = ButtonSize.Small) }
        } else {
            null
        },
    )
}

/** Shaped like the cards on their way, so nothing jumps when they land. */
@Composable
private fun HomeSkeleton() {
    KitSkeleton(height = 196.dp, shape = HeroShape)
    KitSkeleton(height = 136.dp)
    KitSkeleton(height = 120.dp)
    KitSkeleton(height = 260.dp)
}

/**
 * Asked once, at the first cheer or nudge sent or received (REBUILD_PLAN.md
 * §6), when what notifications are for is on the screen. Only "Allow" spends
 * the system prompt, since on Android 13+ one denial cannot be undone in the app.
 */
@Composable
private fun NotificationPrimer(show: Boolean, onAnswered: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val needed = show &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    if (!needed) return
    KitDialog(
        title = stringResource(R.string.social_primer_title),
        body = stringResource(R.string.social_primer_body),
        confirmText = stringResource(R.string.notif_primer_allow),
        onConfirm = {
            onAnswered()
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        },
        dismissText = stringResource(R.string.common_not_now),
        onDismiss = onAnswered,
    )
}

/** One closable offer when a group's preview ends, then never again for that group. */
@Composable
private fun PreviewEnded(groupName: String, onSeePremium: () -> Unit, onDismiss: () -> Unit) {
    KitDialog(
        title = stringResource(R.string.preview_ended_title),
        body = stringResource(R.string.preview_ended_body, groupName),
        confirmText = stringResource(R.string.premium_see),
        onConfirm = onSeePremium,
        dismissText = stringResource(R.string.common_not_now),
        onDismiss = onDismiss,
    )
}

/** Once per group per week. A moment, not confetti. */
@Composable
private fun GoalCelebration(steps: Long, target: Int, onDismiss: () -> Unit) {
    KitDialog(
        title = stringResource(R.string.group_celebration_title),
        confirmText = stringResource(R.string.group_celebration_confirm),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        KitCard(tone = CardTone.Hero, spacing = 4.dp) {
            Text("%,d".format(steps), style = Kit.type.bigNumber.copy(fontSize = 52.sp, lineHeight = 52.sp))
            Text(
                stringResource(R.string.group_celebration_progress, "%,d".format(target)),
                style = Kit.type.label,
                color = mutedContentColor(),
            )
        }
    }
}
