package com.pamoja.app.ui.group

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pamoja.app.ui.theme.Gray300
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
import com.pamoja.app.ui.components.PamojaEmptyState
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.toSnackbarMessage
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing
import com.pamoja.app.util.InviteLink
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// Medal accent for ranks 2 & 3 (rank 1 uses the theme amber). Neutral in both themes.
private val SilverMedal = Gray300
private val BronzeMedal = Color(0xFFCD7F32)

@Composable
fun GroupScreen(
    groupId: String,
    onBack: (() -> Unit)? = null,
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

    // Snackbar text is shown from coroutine scopes and permission callbacks,
    // none of which are composable scopes.
    val healthPermissionNeeded = stringResource(R.string.group_health_permission_needed)
    val inviteInactive = stringResource(R.string.group_invite_inactive)
    val healthUnavailable = stringResource(R.string.group_health_unavailable)
    val shareChooserTitle = stringResource(R.string.group_share_invite)

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
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // ── Top bar ───────────────────────────────────────────────
                item {
                    GroupTopBar(
                        groupName   = uiState.group?.name ?: "",
                        memberCount = uiState.memberStepData.size,
                        onBack      = onBack,
                        onShare     = {
                            if (uiState.group?.inviteLinkActive == false) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(inviteInactive)
                                }
                            } else {
                                // Bare verified https link, nothing wrapped around it.
                                val sendIntent = android.content.Intent(
                                    android.content.Intent.ACTION_SEND
                                ).apply {
                                    type = "text/plain"
                                    putExtra(
                                        android.content.Intent.EXTRA_TEXT,
                                        InviteLink.build(groupId)
                                    )
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(sendIntent, shareChooserTitle)
                                )
                            }
                        }
                    )
                }

                item {
                    OfflineBanner(
                        isOffline = uiState.isOffline,
                        lastUpdatedLabel = if (uiState.hasContent) {
                            stringResource(R.string.offline_group_stale)
                        } else null,
                    )
                }

                // ── Progress ring card ─────────────────────────────────────
                item {
                    GroupProgressCard(
                        combinedSteps = uiState.combinedWeeklySteps,
                        weeklyTarget  = uiState.group?.weeklyTarget?.toLong() ?: 70_000L,
                        daysLeft      = daysLeft,
                        memberCount   = uiState.memberStepData.size
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

                // ── Leaderboard section label ─────────────────────────────
                item {
                    Text(
                        text     = stringResource(R.string.group_leaderboard),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = colors.textTertiary,
                        modifier = Modifier.padding(
                            start = Spacing.x6, end = Spacing.x6,
                            top = Spacing.x5, bottom = Spacing.x3
                        )
                    )
                }

                // ── Ranked rows ───────────────────────────────────────────
                itemsIndexed(uiState.memberStepData) { index, memberData ->
                    LeaderboardRow(
                        rank          = index + 1,
                        user          = memberData.user,
                        todaySteps    = memberData.todaySteps,
                        weeklySteps   = memberData.weeklySteps,
                        isCurrentUser = memberData.user.userId == uiState.currentUserId
                    )
                    if (index < uiState.memberStepData.lastIndex) {
                        Spacer(modifier = Modifier.height(Spacing.x2))
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─── Top bar ─────────────────────────────────────────────────────────────────
@Composable
fun GroupTopBar(
    groupName: String,
    memberCount: Int,
    onShare: () -> Unit,
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
                        pluralStringResource(R.plurals.member_count, memberCount, memberCount)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }

        // Share invite link, available to everyone (members can also invite friends)
        IconButton(onClick = onShare) {
            Icon(
                painter            = painterResource(PamojaIcons.Share),
                contentDescription = stringResource(R.string.group_share_desc),
                tint               = colors.accentPrimary,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Progress ring card ────────────────────────────────────────────────────────
@Composable
fun GroupProgressCard(
    combinedSteps: Long,
    weeklyTarget: Long,
    daysLeft: Int,
    memberCount: Int
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
        // Progress ring
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.size(180.dp)
        ) {
            // Track (stage-tinted dim ring, visible in both themes)
            CircularProgressIndicator(
                progress    = { 1f },
                modifier    = Modifier.size(180.dp),
                color       = ringColor.copy(alpha = 0.16f),
                strokeWidth = 14.dp,
                strokeCap   = StrokeCap.Round
            )
            // Filled progress ring
            CircularProgressIndicator(
                progress    = { progress },
                modifier    = Modifier.size(180.dp),
                color       = ringColor,
                strokeWidth = 14.dp,
                strokeCap   = StrokeCap.Round
            )
            // Center content
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text  = "%,d".format(combinedSteps),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp),
                    color = colors.textPrimary
                )
                Text(
                    text  = stringResource(R.string.group_of_target, "%,d".format(weeklyTarget)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary
                )
                Spacer(modifier = Modifier.height(Spacing.x1))
                Text(
                    text  = "$daysLeft days left",
                    style = MaterialTheme.typography.labelSmall,
                    color = ringColor
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

// ─── Stat pill ────────────────────────────────────────────────────────────────
@Composable
fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPamojaColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(PamojaRadii.sm))
            .background(colors.surfaceSunken)
            .padding(vertical = Spacing.x3, horizontal = Spacing.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text  = value,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = colors.textTertiary
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
    isCurrentUser: Boolean
) {
    val colors = LocalPamojaColors.current
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

    val medalColor = when (rank) {
        1 -> colors.accentAmber
        2 -> SilverMedal
        3 -> BronzeMedal
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
                if (isFirst) Modifier.padding(horizontal = Spacing.x4, vertical = Spacing.x4)
                else Modifier.padding(horizontal = Spacing.x3, vertical = Spacing.x3)
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

        // Avatar circle with initials
        Box(
            modifier = Modifier
                .size(if (isFirst) 44.dp else 36.dp)
                .clip(CircleShape)
                .background(
                    brush = if (isCurrentUser)
                        Brush.linearGradient(listOf(colors.accentPrimary, colors.accentPrimaryPress))
                    else
                        Brush.linearGradient(listOf(colors.accentPrimarySubtle, colors.accentPrimarySubtle))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = user.name.take(2).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (isFirst) 14.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isCurrentUser) colors.textOnBrand else colors.accentPrimary
            )
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
                text  = "%,d this week".format(weeklySteps),
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary)
            )
        }

        // Today's steps (right side)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text  = "%,d".format(todaySteps),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize   = if (isFirst) 15.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isFirst) colors.accentAmber else colors.textPrimary
                )
            )
            Text(
                text  = stringResource(R.string.group_today),
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
