package com.pamoja.app.ui.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.pamoja.app.ui.theme.DisplayFontFamily
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.WeekWindow
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.NoticeTone
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.GroupDashboardSkeleton
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.formatSyncTime
import com.pamoja.app.ui.components.PamojaEmptyState
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.toSnackbarMessage
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing
import com.pamoja.app.util.InviteLink
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Which range the leaderboard ranks by.
 *
 * Only two. The design shows a third, "All", but every step figure on this
 * screen comes from the Monday-to-Sunday query, so an all-time column would
 * either need its own aggregate or would quietly show week totals under a
 * lifetime label. A tab that lies is worse than a tab that is missing.
 */
enum class LeaderboardRange { Today, Week }

/** Days in the shared week. The window is set per group, see WeekWindow. */
private const val DaysInWeek = 7

/**
 * Above this many members the leaderboard switches to compact rows.
 *
 * From the states deck: density absorbs group size, structure never changes.
 * Twelve is where the full-size rows stop fitting a screen without the top of
 * the list scrolling away before you have found yourself in it.
 */
private const val CompactLeaderboardFrom = 12

/** How far the ring dims when its numbers may be out of date. From the deck. */
private const val StaleRingAlpha = 0.70f

// Ring geometry, from the design's 246px ring with a 19px stroke inside a 392px
// frame. Kept as constants because the two indicators and the layout that sizes
// them have to agree exactly or the track and the fill drift apart.
private val RingSize = 220.dp
private val RingStroke = 18.dp

/**
 * The widest a stacked column may be inside the ring without touching it.
 *
 * The square inscribed in a circle has a side of d / sqrt(2), roughly 0.707d.
 * Derived from the ring rather than written as a number so it stays correct if
 * the ring is ever resized.
 */
private val RingInnerSafeWidth = (RingSize - RingStroke * 2) * 0.70f

// PullToRefreshBox is still marked experimental in Material 3. It is the
// official API and the alternative is hand-rolling the gesture, which would be
// worse and would still have to be replaced later.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    groupId: String,
    onBack: (() -> Unit)? = null,
    onShareInvite: () -> Unit,
    onEditGroup: () -> Unit,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val uiState              by viewModel.uiState.collectAsState()
    val snackbarHostState    = remember { SnackbarHostState() }
    val userPreferences      = viewModel.userPreferences
    val isHealthConnectGranted by userPreferences.isHealthConnectGranted
        .collectAsState(initial = false)
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Which column the leaderboard ranks by. Screen state rather than UiState:
    // it changes nothing in the domain and touches no repository, it only picks
    // which of two numbers already in hand is the one being compared.
    var leaderboardRange by rememberSaveable { mutableStateOf(LeaderboardRange.Today) }

    // Re-ranked for the chosen range, not just re-labelled: the medal has to
    // mean the column it sits next to. Computed here rather than inside the
    // LazyColumn because a LazyListScope is not a composable scope.
    val rankedMembers = remember(uiState.memberStepData, leaderboardRange) {
        uiState.memberStepData.sortedByDescending {
            if (leaderboardRange == LeaderboardRange.Today) it.todaySteps else it.weeklySteps
        }
    }

    // Snackbar text is shown from coroutine scopes and permission callbacks,
    // none of which are composable scopes.
    val healthPermissionNeeded = stringResource(R.string.group_health_permission_needed)
    val inviteInactive = stringResource(R.string.group_invite_inactive)
    val healthUnavailable = stringResource(R.string.group_health_unavailable)
    val shareChooserTitle = stringResource(R.string.group_share_invite)

    // Formatted once. Null until a sync has ever happened, which renders as no
    // timestamp rather than as the epoch.
    val syncedAt = formatSyncTime(context, uiState.lastSyncedAt)

    // Health Connect permissions, the same contract the onboarding screen uses.
    //
    // This previously requested Manifest.permission.ACTIVITY_RECOGNITION, which
    // was broken twice over: that permission is not declared in the manifest, so
    // Android denied it instantly without ever showing a dialog, and it is not
    // the permission this app reads steps with anyway. It was left over from the
    // old raw sensor implementation that has now been deleted.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        scope.launch {
            val hasPermission = granted.containsAll(HealthConnectReader.REQUIRED_PERMISSIONS)
            userPreferences.setHealthConnectGranted(hasPermission)
            if (!hasPermission) {
                snackbarHostState.showSnackbar(healthPermissionNeeded)
            }
        }
    }

    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }

    // Only action failures reach the snackbar. Load failures own the screen
    // instead, so the user is never told the same thing in two places at once.
    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            snackbarHostState.showSnackbar(it.toSnackbarMessage(context))
            viewModel.clearActionError()
        }
    }

    // Health Connect access can be revoked from outside the app entirely, and
    // nothing tells us when it happens. Re-checking on every resume is the only
    // reliable signal, and it is cheap.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHealthConnectStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val today      = LocalDate.now()
    val endOfWeek  = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val daysLeft   = ChronoUnit.DAYS.between(today, endOfWeek).toInt() + 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        if (uiState.showSkeleton) {
            // Shaped like the ring and leaderboard that follow, so the layout
            // does not jump when the real numbers land.
            GroupDashboardSkeleton(modifier = Modifier.fillMaxSize())
        } else if (uiState.isGroupUnavailable) {
            // Deleted, or we were removed. Retrying cannot fix either, so the
            // only honest action is a way back.
            PamojaEmptyState(
                icon = PamojaIcons.Users,
                title = stringResource(R.string.group_unavailable_title),
                body = stringResource(R.string.group_unavailable_body),
                actionLabel = stringResource(R.string.common_back_to_groups),
                onAction = { onBack?.invoke() },
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (uiState.fatalError != null && !uiState.hasContent) {
            PamojaErrorState(
                error = uiState.fatalError!!,
                onRetry = { viewModel.retry() },
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            // Wraps the list only, so the gesture exists where there is content
            // to refresh and never competes with the full-screen error above.
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // ── Top bar ───────────────────────────────────────────────
                item {
                    GroupTopBar(
                        groupName   = uiState.group?.name ?: "",
                        memberCount = uiState.memberStepData.size,
                        isAdmin     = uiState.isAdmin,
                        isFull      = uiState.group?.let {
                            it.maxMemberCap > 0 && it.memberCount >= it.maxMemberCap
                        } ?: false,
                        startDay    = uiState.group?.startDay ?: WeekWindow.LEGACY_START_DAY,
                        onEditGroup = onEditGroup,
                        onBack      = onBack,
                        onShare     = {
                            if (uiState.group?.inviteLinkActive == false) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(inviteInactive)
                                }
                            } else {
                                // Opens the invite screen rather than firing the
                                // system sheet straight away. Sharing a group in
                                // person means showing a QR code, and that only
                                // existed on the screen you saw once at creation
                                // and could never get back to. The invite screen
                                // already offers the QR, the link, copy, and the
                                // same system sheet.
                                onShareInvite()
                            }
                        }
                    )
                }

                item {
                    OfflineBanner(
                        isOffline = uiState.isOffline,
                        // A real time when there is one. "Showing old data"
                        // without saying how old is barely a statement, and the
                        // worker already records exactly this moment.
                        lastUpdatedLabel = when {
                            !uiState.hasContent -> null
                            syncedAt != null ->
                                stringResource(R.string.offline_showing_from, syncedAt)
                            else -> stringResource(R.string.offline_group_stale)
                        },
                    )
                }

                // ── Progress ring card ─────────────────────────────────────
                item {
                    GroupProgressCard(
                        combinedSteps = uiState.combinedWeeklySteps,
                        weeklyTarget  = uiState.group?.weeklyTarget?.toLong() ?: 70_000L,
                        daysLeft      = daysLeft,
                        memberCount   = uiState.memberStepData.size,
                        isStale       = uiState.isOffline,
                        lastSyncedAt  = syncedAt,
                    )
                }

                // ── What that means per person, per day ────────────────────
                //
                // Also withheld while offline. It reads the same pace as the
                // badge and phrases it as advice, so a stale one tells people
                // to walk further because their phone lost signal.
                if (!uiState.isOffline) item {
                    GroupInsightCard(
                        combinedSteps = uiState.combinedWeeklySteps,
                        weeklyTarget  = uiState.group?.weeklyTarget?.toLong() ?: 70_000L,
                        daysLeft      = daysLeft,
                        memberCount   = uiState.memberStepData.size,
                        modifier      = Modifier.padding(
                            horizontal = Spacing.x6,
                            vertical   = Spacing.x3,
                        ),
                    )
                }

                // Partial failure: the group and its members are here, the step
                // totals are not. Says so rather than showing a row of zeroes
                // that reads as "nobody has walked".
                if (uiState.stepsError != null) {
                    item {
                        PamojaNotice(
                            icon = PamojaIcons.AlertCircle,
                            title = stringResource(R.string.group_steps_failed_title),
                            body = stringResource(R.string.group_steps_failed_body),
                            tone = NoticeTone.Warning,
                            modifier = Modifier.padding(horizontal = Spacing.x6),
                        )
                    }
                }

                // ── Leaderboard section label and range toggle ────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = Spacing.x6, end = Spacing.x6,
                                top = Spacing.x5, bottom = Spacing.x3
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            // Names the moment these standings were true, so a
                            // leaderboard nobody has been able to update does
                            // not read as live.
                            text = if (uiState.isOffline && syncedAt != null) {
                                stringResource(R.string.group_leaderboard_as_of, syncedAt)
                            } else {
                                stringResource(R.string.group_leaderboard)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                        LeaderboardRangeToggle(
                            selected = leaderboardRange,
                            onSelect = { leaderboardRange = it },
                        )
                    }
                }

                // ── Ranked rows ───────────────────────────────────────────
                //
                // Density changes with size, structure does not. Past
                // CompactLeaderboardFrom the rows tighten so a full group is
                // still scannable; two members and twenty are the same screen.
                itemsIndexed(rankedMembers) { index, memberData ->
                    LeaderboardRow(
                        rank          = index + 1,
                        user          = memberData.user,
                        todaySteps    = memberData.todaySteps,
                        weeklySteps   = memberData.weeklySteps,
                        range         = leaderboardRange,
                        isCurrentUser = memberData.user.userId == uiState.currentUserId,
                        compact       = rankedMembers.size > CompactLeaderboardFrom,
                    )
                    if (index < rankedMembers.lastIndex) {
                        Spacer(modifier = Modifier.height(Spacing.x2))
                    }
                }

                // A group of one is unfinished, not empty. The dashed row is
                // the second slot waiting to be filled, so the leaderboard
                // never reads as a ranking with nobody to rank against.
                if (rankedMembers.size == 1) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.x2))
                        SoloInviteRow(onClick = onShareInvite)
                    }
                }

                // Two people are a partnership. Saying so, with the room that
                // is left, beats a two-row ranking that declares a loser.
                if (rankedMembers.size == 2) {
                    val room = (uiState.group?.maxMemberCap ?: 0) - 2
                    if (room > 0) {
                        item {
                            Spacer(modifier = Modifier.height(Spacing.x3))
                            Text(
                                text = pluralStringResource(
                                    R.plurals.group_pair_room, room, room
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(horizontal = Spacing.x6),
                            )
                        }
                    }
                }

                // Everyone is on zero. A brand new group is the most common
                // reason, so this reads as a starting line rather than a fault.
                if (uiState.showNoStepsYet) {
                    item {
                        PamojaEmptyState(
                            icon = PamojaIcons.Footprints,
                            title = stringResource(R.string.group_no_steps_title),
                            body = stringResource(R.string.group_no_steps_body),
                        )
                    }
                }

                // ── Health connect prompt (only when not granted) ─────────
                if (!isHealthConnectGranted) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.x4))
                        HealthConnectCard(onClick = {
                            scope.launch {
                                val reader = viewModel.healthConnectReader
                                when {
                                    !reader.isAvailable() -> snackbarHostState.showSnackbar(healthUnavailable)
                                    reader.hasPermission() -> {
                                        // Already granted, the local flag was just stale.
                                        userPreferences.setHealthConnectGranted(true)
                                    }
                                    else -> permissionLauncher.launch(
                                        HealthConnectReader.REQUIRED_PERMISSIONS
                                    )
                                }
                            }
                        })
                    }
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * The empty second place in a group of one.
 *
 * Drawn as a dashed outline rather than a filled row, so it reads as a slot
 * waiting to be filled rather than a member who scored nothing. This is the
 * whole point of the state: a new group is unfinished, not broken, and the one
 * useful action sits inside the gap it is describing.
 */
@Composable
private fun SoloInviteRow(onClick: () -> Unit) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.md)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6)
            .clip(shape)
            .dashedBorder(colors.borderDefault, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.x3, vertical = Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(PamojaIcons.Add),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.group_solo_invite_title),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accentPrimary,
            )
            Text(
                text = stringResource(R.string.group_solo_invite_sub),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }

        Icon(
            painter = painterResource(PamojaIcons.ChevronRight),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * A dashed outline, which Compose has no modifier for.
 *
 * Drawn rather than approximated with a solid border, because the dash is what
 * carries the meaning here: solid would read as a real row.
 */
private fun Modifier.dashedBorder(color: Color, shape: Shape): Modifier =
    this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
        )
        when (outline) {
            is Outline.Rounded -> drawPath(Path().apply { addRoundRect(outline.roundRect) }, color, style = stroke)
            is Outline.Rectangle -> drawRect(color, style = stroke)
            is Outline.Generic -> drawPath(outline.path, color, style = stroke)
        }
    }

// ─── Top bar ─────────────────────────────────────────────────────────────────
/**
 * Names the group's week window for the header.
 *
 * Only the two conventions the picker offers get a paired abbreviation. Any
 * other stored value, which can only come from a later release, is named by its
 * first day rather than inventing five more abbreviation pairs that would each
 * need translating.
 */
@Composable
private fun weekWindowLabel(startDay: DayOfWeek): String = when (startDay) {
    DayOfWeek.MONDAY -> stringResource(R.string.group_week_mon_sun)
    DayOfWeek.SUNDAY -> stringResource(R.string.group_week_sun_sat)
    else -> stringResource(
        R.string.group_week_starts,
        startDay.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
    )
}

@Composable
fun GroupTopBar(
    groupName: String,
    memberCount: Int,
    onShare: () -> Unit,
    isAdmin: Boolean,
    isFull: Boolean,
    startDay: DayOfWeek,
    onEditGroup: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val colors = LocalPamojaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.x2, vertical = Spacing.x2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    painter            = painterResource(PamojaIcons.ArrowLeft),
                    contentDescription = stringResource(R.string.group_back_desc),
                    tint               = colors.textSecondary,
                    modifier           = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(Spacing.x4))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = groupName,
                style    = MaterialTheme.typography.headlineSmall,
                color    = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (memberCount > 0) {
                Text(
                    text  = stringResource(
                        R.string.group_header_subtitle,
                        pluralStringResource(R.plurals.member_count, memberCount, memberCount),
                        weekWindowLabel(startDay),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }

        // Share invite link, available to everyone (members can also invite
        // friends). A full group shows a chip instead: the control is not
        // disabled and silent, it says why it is gone.
        if (isFull) {
            Text(
                text = stringResource(R.string.group_full_chip),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.surface2)
                    .padding(horizontal = Spacing.x3, vertical = Spacing.x1),
            )
            Spacer(modifier = Modifier.width(Spacing.x2))
        } else {
            IconButton(onClick = onShare) {
                Icon(
                    painter            = painterResource(PamojaIcons.Share),
                    contentDescription = stringResource(R.string.group_share_desc),
                    tint               = colors.accentPrimary,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        // Settings, admin only. Hidden rather than disabled for everyone else,
        // since a control that is always refused is worse than no control.
        if (isAdmin) {
            IconButton(onClick = onEditGroup) {
                Icon(
                    painter            = painterResource(PamojaIcons.Settings),
                    contentDescription = stringResource(R.string.group_edit_action),
                    tint               = colors.textSecondary,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─── Progress ring card ────────────────────────────────────────────────────────
@Composable
fun GroupProgressCard(
    combinedSteps: Long,
    weeklyTarget: Long,
    daysLeft: Int,
    memberCount: Int,
    /**
     * These figures may be out of date, so the card stops asserting them.
     *
     * The ring dims and the pace badge is withheld rather than recalculated.
     * Pace compares progress against how much of the week has passed, and time
     * keeps passing while the steps do not arrive, so an offline pace reading
     * drifts from "on track" to "behind" purely because the network is down.
     * That is the app inventing bad news about someone who may be walking.
     */
    isStale: Boolean = false,
    lastSyncedAt: String? = null,
) {
    val colors = LocalPamojaColors.current
    val progress = if (weeklyTarget > 0) {
        (combinedSteps.toFloat() / weeklyTarget.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val remaining = (weeklyTarget - combinedSteps).coerceAtLeast(0L)

    // Ring colour changes with progress, amber early, indigo mid, teal when done.
    val ringColor = when {
        progress >= 1f   -> colors.statusSuccess
        progress >= 0.6f -> colors.accentPrimary
        else             -> colors.accentAmber
    }

    val cardShape = RoundedCornerShape(PamojaRadii.xl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6)
            .shadow(10.dp, cardShape, clip = false)
            .clip(cardShape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(Spacing.x6),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress ring. Heavier and larger than before, per the design: this is
        // the one number the product exists to show, so it carries the screen.
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.size(RingSize)
        ) {
            // Track is a flat surface tone rather than a tint of the fill, so it
            // stays legible when the fill is nearly empty.
            CircularProgressIndicator(
                progress    = { 1f },
                modifier    = Modifier.size(RingSize),
                color       = colors.surface2,
                strokeWidth = RingStroke,
                strokeCap   = StrokeCap.Round,
                gapSize     = 0.dp,
            )
            CircularProgressIndicator(
                progress    = { progress },
                // Dimmed while stale. The ring is the loudest thing on the
                // screen and the most confident, so it is the first thing that
                // should stop looking certain.
                modifier    = Modifier
                    .size(RingSize)
                    .alpha(if (isStale) StaleRingAlpha else 1f),
                color       = ringColor,
                strokeWidth = RingStroke,
                strokeCap   = StrokeCap.Round,
                trackColor  = Color.Transparent,
                gapSize     = 0.dp,
            )
            // Center content, held inside the ring's safe square.
            //
            // A circle only gives a stacked column the width of the square
            // inscribed in it, not the full inner diameter. Unconstrained, the
            // pace badge grew past that as soon as the system font was scaled
            // up and crowded the ring itself, which is what made it look
            // oversized and off centre.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(RingInnerSafeWidth),
            ) {
                // Withheld while stale, and replaced by when these numbers were
                // last true. Suppressed rather than guessed, per the states
                // deck: a pace reading that decays on its own is worse than no
                // pace reading.
                if (isStale && lastSyncedAt != null) {
                    Text(
                        text = stringResource(R.string.group_last_synced, lastSyncedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                } else if (!isStale) {
                    PaceBadge(progress = progress, daysLeft = daysLeft)
                }
                Spacer(modifier = Modifier.height(Spacing.x2))
                // Never wraps. This is the one number the product exists to
                // show, and clamping the column to the ring's safe square is
                // what made it break: at a larger font scale "14,376" no longer
                // fitted and split across two lines mid-number.
                //
                // Shrinking is the right trade against wrapping here. A step
                // count is read as a single quantity, and the digits carry no
                // meaning once they are cut in half.
                BasicText(
                    text = "%,d".format(combinedSteps),
                    style = MaterialTheme.typography.displayLarge.copy(
                        color = colors.textPrimary,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 24.sp,
                        maxFontSize = MaterialTheme.typography.displayLarge.fontSize,
                        stepSize = 1.sp,
                    ),
                )
                Text(
                    text  = stringResource(R.string.group_of_target, "%,d".format(weeklyTarget)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary
                )
                Spacer(modifier = Modifier.height(Spacing.x2))
                // A pill, not loose text. It is a distinct fact from the number
                // above it and the design separates the two that way.
                Text(
                    text  = pluralStringResource(R.plurals.group_days_left, daysLeft, daysLeft),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(colors.surface2)
                        .padding(horizontal = Spacing.x3, vertical = Spacing.x1),
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.x5))

        // Stat pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x2)
        ) {
            StatPill(
                label    = stringResource(R.string.group_stat_completed),
                value    = "${(progress * 100).toInt()}%",
                // The only tile the design colours. It is the one that answers
                // "how are we doing"; the other two are supporting detail.
                valueColor = colors.accentPrimary,
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label    = stringResource(R.string.group_stat_remaining),
                value    = "%,d".format(remaining),
                modifier = Modifier.weight(1f)
            )
            StatPill(
                // The label carries the noun, so it has to agree with the
                // number sitting above it.
                label    = pluralStringResource(R.plurals.member_label, memberCount),
                value    = "$memberCount",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Whether the group is keeping up with its own week.
 *
 * The benchmark is whole days already gone, not a fraction of today: with two
 * days left you are measured against the five that finished, which is the
 * generous reading. Measuring against part of the current day would put a group
 * "behind" at nine in the morning, every morning.
 *
 * There is no failing state on purpose. The brief's rule for this product's copy
 * is that it never shames, so falling short reads as an invitation rather than a
 * verdict, and it is amber rather than red.
 */
private enum class Pace { Complete, OnTrack, Behind }

/**
 * Shared by the badge and the insight card below it. Computed once because two
 * elements sitting inches apart, both describing pace, must never disagree.
 */
private fun paceOf(progress: Float, daysLeft: Int): Pace {
    val daysElapsed = (DaysInWeek - daysLeft).coerceIn(0, DaysInWeek)
    val expected = daysElapsed.toFloat() / DaysInWeek
    return when {
        progress >= 1f -> Pace.Complete
        progress >= expected -> Pace.OnTrack
        else -> Pace.Behind
    }
}

@Composable
private fun PaceBadge(progress: Float, daysLeft: Int) {
    val colors = LocalPamojaColors.current

    val (labelRes, tint, tone) = when (paceOf(progress, daysLeft)) {
        Pace.Complete -> Triple(
            R.string.group_pace_complete, colors.statusSuccess, colors.statusSuccessSubtle
        )
        Pace.OnTrack -> Triple(
            R.string.group_pace_on_track, colors.accentPrimary, colors.accentPrimarySubtle
        )
        Pace.Behind -> Triple(
            R.string.group_pace_keep_going, colors.accentAmber, colors.accentAmberSubtle
        )
    }

    // Trimmed to earn its place inside the ring. The badge is a caption on the
    // number below it, not a control, so it should read as the smallest thing
    // in the circle rather than competing with the step count.
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(tone)
            .padding(horizontal = Spacing.x2, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(PamojaIcons.Flame),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        // One line, always. Uppercase is roughly a fifth wider than sentence
        // case, so this is the part that overflows first at a large font scale,
        // and a wrapped pace badge inside a circle looks broken rather than
        // merely tight.
        BasicText(
            text = stringResource(labelRes).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(color = tint),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
                stepSize = 0.5.sp,
            ),
        )
    }
}

/**
 * The one line that turns the ring into an instruction.
 *
 * A weekly group total is not actionable; what a person can act on is how far
 * they personally have to walk tomorrow. That is the remainder split by the days
 * still left and the people still walking.
 *
 * Rendered as one sentence with the number emphasised rather than as a stat and
 * a caption, because it is meant to read as a sentence. The number is located in
 * the resolved string rather than concatenated, so translations stay free to put
 * it wherever their grammar wants it.
 */
@Composable
private fun GroupInsightCard(
    combinedSteps: Long,
    weeklyTarget: Long,
    daysLeft: Int,
    memberCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current

    // Nobody to divide by yet. The members are still loading, and "Infinity
    // steps a day each" is not a thing to show anyone.
    if (memberCount < 1) return

    val progress = if (weeklyTarget > 0) {
        (combinedSteps.toFloat() / weeklyTarget.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val pace = paceOf(progress, daysLeft)
    val remaining = (weeklyTarget - combinedSteps).coerceAtLeast(0L)

    // Rounded up: rounding down would print a daily figure that does not
    // actually reach the target by Sunday.
    val perPersonPerDay = if (remaining == 0L) 0L else {
        val divisor = daysLeft.coerceAtLeast(1).toLong() * memberCount
        (remaining + divisor - 1) / divisor
    }

    val tint = if (pace == Pace.Behind) colors.accentAmber else colors.accentTeal
    val tone = if (pace == Pace.Behind) colors.accentAmberSubtle else colors.accentTealSubtle

    val number = "%,d".format(perPersonPerDay)
    val sentence = when (pace) {
        Pace.Complete -> stringResource(R.string.group_insight_complete)
        Pace.OnTrack  -> stringResource(R.string.group_insight_on_track, number)
        Pace.Behind   -> stringResource(R.string.group_insight_behind, number)
    }

    val styled = remember(sentence, number, pace, colors) {
        buildAnnotatedString {
            append(sentence)
            val start = sentence.indexOf(number)
            if (pace != Pace.Complete && start >= 0) {
                addStyle(
                    SpanStyle(
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    ),
                    start,
                    start + number.length,
                )
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PamojaRadii.lg))
            .background(tone)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        Icon(
            painter = painterResource(PamojaIcons.TrendingUp),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = styled,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

/**
 * Today / Week, as the design's pill inside a pill: a sunken track with the
 * active option raised onto a surface chip. The unselected options stay full
 * contrast rather than being greyed, since both are equally valid views.
 */
@Composable
private fun LeaderboardRangeToggle(
    selected: LeaderboardRange,
    onSelect: (LeaderboardRange) -> Unit,
) {
    val colors = LocalPamojaColors.current
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(colors.surface2)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LeaderboardRange.entries.forEach { range ->
            val isSelected = range == selected
            val labelRes = when (range) {
                LeaderboardRange.Today -> R.string.group_range_today
                LeaderboardRange.Week  -> R.string.group_range_week
            }
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .then(
                        if (isSelected) Modifier.background(colors.surface1)
                        else Modifier
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(range) },
                    )
                    .padding(horizontal = Spacing.x3, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.textPrimary else colors.textTertiary,
                )
            }
        }
    }
}

// ─── Stat pill ────────────────────────────────────────────────────────────────
@Composable
fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = LocalPamojaColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(PamojaRadii.lg))
            .background(colors.surface2)
            .padding(vertical = Spacing.x3, horizontal = Spacing.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text  = value,
            style = MaterialTheme.typography.headlineSmall,
            color = valueColor ?: colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text  = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

// ─── Leaderboard row ──────────────────────────────────────────────────────────
// Rank #1 gets an amber-tinted, taller card; the current user gets an indigo tint.
@Composable
fun LeaderboardRow(
    rank: Int,
    user: User,
    todaySteps: Long,
    weeklySteps: Long,
    range: LeaderboardRange,
    isCurrentUser: Boolean,
    /**
     * Tighter rows for a large group.
     *
     * First place keeps its raised card and the current user keeps their
     * highlight either way, because those are the two rows anyone is actually
     * looking for. Only the spacing and the avatar shrink.
     */
    compact: Boolean = false,
) {
    val colors = LocalPamojaColors.current
    // Compact never applies to first place: it is the one row the design
    // deliberately raises above the rest, at any group size.
    val isFirst = rank == 1

    // Base surface + optional tint overlay + border, all theme-aware.
    val tintOverlay = when {
        isFirst       -> colors.accentAmberSubtle
        isCurrentUser -> colors.accentPrimarySubtle
        else          -> Color.Transparent
    }
    val borderColor = when {
        isFirst       -> colors.accentAmber.copy(alpha = 0.45f)
        isCurrentUser -> colors.accentPrimaryBorder
        else          -> colors.borderSubtle
    }

    // All three follow the theme now. Bronze was a raw hex and silver borrowed a
    // neutral from the ramp, so neither moved between light and dark.
    val medalColor = when (rank) {
        1 -> colors.medalGold
        2 -> colors.medalSilver
        3 -> colors.medalBronze
        else -> colors.textTertiary
    }

    val rowShape = RoundedCornerShape(if (isFirst) PamojaRadii.lg else PamojaRadii.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6)
            .clip(rowShape)
            .background(colors.surface1)
            .background(tintOverlay)
            .border(1.dp, borderColor, rowShape)
            .then(
                when {
                    isFirst -> Modifier.padding(
                        horizontal = Spacing.x4, vertical = Spacing.x4
                    )
                    compact -> Modifier.padding(
                        horizontal = Spacing.x3, vertical = Spacing.x2
                    )
                    else -> Modifier.padding(
                        horizontal = Spacing.x3, vertical = Spacing.x3
                    )
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3)
    ) {
        // Rank indicator, medal icon for top 3, number otherwise
        Box(
            modifier = Modifier.width(if (isFirst) 28.dp else 22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (rank <= 3) {
                Icon(
                    painter            = painterResource(PamojaIcons.Medal),
                    contentDescription = stringResource(R.string.group_rank_desc, rank),
                    tint               = medalColor,
                    modifier           = Modifier.size(if (isFirst) 24.dp else 20.dp)
                )
            } else {
                Text(
                    text  = "$rank",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrentUser) colors.accentPrimary else colors.textTertiary
                )
            }
        }

        // Avatar: the member's photo when they have chosen to share it, and
        // their initials otherwise.
        //
        // A blank photoUrl here is not a missing image, it is someone who left
        // the sharing preference off, which is the default. Initials are the
        // designed state rather than a fallback, so they must not look like a
        // failed load.
        val avatarSize = when {
            isFirst -> 44.dp
            compact -> 30.dp
            else -> 36.dp
        }
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(
                    brush = if (isCurrentUser)
                        Brush.linearGradient(listOf(colors.accentPrimary, colors.accentPrimaryPress))
                    else
                        Brush.linearGradient(listOf(colors.accentPrimarySubtle, colors.accentPrimarySubtle))
                ),
            contentAlignment = Alignment.Center
        ) {
            val photo = user.photoUrl
            if (!photo.isNullOrBlank()) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape),
                )
            } else {
                Text(
                    text  = user.name.take(2).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isFirst) 14.sp else 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isCurrentUser) colors.textOnBrand else colors.accentPrimary
                )
            }
        }

        // Name + weekly steps
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.x2)
            ) {
                Text(
                    text     = user.name,
                    style    = if (isFirst)
                        MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary)
                    else
                        MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // "you" badge
                if (isCurrentUser) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(PamojaRadii.xs))
                            .background(colors.accentPrimarySubtle)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text  = stringResource(R.string.group_you),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = colors.accentPrimary
                        )
                    }
                }
            }
            Text(
                // The range not currently being ranked by, so both numbers stay
                // visible and switching the toggle never hides information.
                text  = if (range == LeaderboardRange.Today) {
                    stringResource(R.string.group_steps_this_week, "%,d".format(weeklySteps))
                } else {
                    stringResource(R.string.group_steps_today, "%,d".format(todaySteps))
                },
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary)
            )
        }

        // The ranked figure (right side). This is the column the order and the
        // medals are based on, so it follows the toggle.
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text  = "%,d".format(
                    if (range == LeaderboardRange.Today) todaySteps else weeklySteps
                ),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize   = if (isFirst) 15.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isFirst) colors.accentAmber else colors.textPrimary
                )
            )
            Text(
                text  = stringResource(
                    if (range == LeaderboardRange.Today) R.string.group_today
                    else R.string.group_range_week
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary
            )
        }
    }
}

// ─── Health connect card ──────────────────────────────────────────────────────
@Composable
fun HealthConnectCard(onClick: () -> Unit) {
    val colors = LocalPamojaColors.current
    val cardShape = RoundedCornerShape(PamojaRadii.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6)
            .clip(cardShape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, cardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(PamojaRadii.xs))
                .background(colors.accentPrimarySubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter            = painterResource(PamojaIcons.Footprints),
                contentDescription = null,
                tint               = colors.accentPrimary,
                modifier           = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text  = stringResource(R.string.group_enable_tracking_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text  = stringResource(R.string.group_enable_tracking_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Icon(
            painter            = painterResource(PamojaIcons.ChevronRight),
            contentDescription = null,
            tint               = colors.textTertiary,
            modifier           = Modifier.size(16.dp)
        )
    }
}
