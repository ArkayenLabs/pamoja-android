package com.pamoja.app.ui.group

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.data.local.health.StepCounterService
import com.pamoja.app.domain.model.User
import com.pamoja.app.ui.theme.PamojaAmber
import com.pamoja.app.ui.theme.PamojaAmberSubtle
import com.pamoja.app.ui.theme.PamojaBackground
import com.pamoja.app.ui.theme.PamojaBorder
import com.pamoja.app.ui.theme.PamojaGreen
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoDark
import com.pamoja.app.ui.theme.PamojaIndigoLight
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaSurface
import com.pamoja.app.ui.theme.PamojaSurfaceHigh
import com.pamoja.app.ui.theme.PamojaSurfaceVariant
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaTextTertiary
import com.pamoja.app.ui.theme.PamojaWhite
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Composable
fun GroupScreen(
    groupId: String,
    onBack: (() -> Unit)? = null,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val uiState              by viewModel.uiState.collectAsState()
    val snackbarHostState    = remember { SnackbarHostState() }
    val userPreferences      = viewModel.userPreferences
    val isHealthConnectGranted by userPreferences.isHealthConnectGranted
        .collectAsState(initial = false)
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            if (granted) {
                userPreferences.setHealthConnectGranted(true)
                try { StepCounterService.start(context) } catch (_: Exception) {}
            } else {
                userPreferences.setHealthConnectGranted(false)
                snackbarHostState.showSnackbar("Permission denied. Enable it later in settings.")
            }
        }
    }

    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val today      = LocalDate.now()
    val endOfWeek  = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val daysLeft   = ChronoUnit.DAYS.between(today, endOfWeek).toInt() + 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PamojaBackground)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                color       = PamojaIndigo,
                strokeWidth = 2.dp,
                modifier    = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // ── Top bar ───────────────────────────────────────────────
                item {
                    GroupTopBar(
                        groupName   = uiState.group?.name ?: "",
                        memberCount = uiState.memberStepData.size,
                        isAdmin     = uiState.isAdmin,
                        onBack      = onBack
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

                // ── Leaderboard section label ─────────────────────────────
                item {
                    Text(
                        text     = "LEADERBOARD",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = PamojaTextTertiary,
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp,
                            top = 20.dp, bottom = 10.dp
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
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // ── Health connect prompt (only when not granted) ─────────
                if (!isHealthConnectGranted) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        HealthConnectCard(onClick = {
                            val alreadyGranted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACTIVITY_RECOGNITION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (alreadyGranted) {
                                scope.launch {
                                    userPreferences.setHealthConnectGranted(true)
                                    try { StepCounterService.start(context) } catch (_: Exception) {}
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                        })
                    }
                }

                item {
                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(32.dp)
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
    isAdmin: Boolean,
    onBack: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint               = PamojaTextSecondary,
                    modifier           = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = groupName,
                style    = MaterialTheme.typography.headlineSmall,
                color    = PamojaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (memberCount > 0) {
                Text(
                    text  = "$memberCount members · Mon–Sun",
                    style = MaterialTheme.typography.labelSmall,
                    color = PamojaTextTertiary
                )
            }
        }

        if (isAdmin) {
            IconButton(onClick = { }) {
                Icon(
                    imageVector        = Icons.Default.Settings,
                    contentDescription = "Group settings",
                    tint               = PamojaTextSecondary,
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
    memberCount: Int
) {
    val progress = if (weeklyTarget > 0) {
        (combinedSteps.toFloat() / weeklyTarget.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val remaining = (weeklyTarget - combinedSteps).coerceAtLeast(0L)

    // Ring colour changes with progress — amber early, indigo mid, green when done
    val ringColor = when {
        progress >= 1f  -> PamojaGreen
        progress >= 0.6f -> PamojaIndigo
        else            -> PamojaAmber
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PamojaSurface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress ring
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.size(180.dp)
        ) {
            // Track (dim background ring)
            CircularProgressIndicator(
                progress    = { 1f },
                modifier    = Modifier.size(180.dp),
                color       = PamojaSurfaceVariant,
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
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PamojaTextPrimary
                )
                Text(
                    text  = "of %,d".format(weeklyTarget),
                    style = MaterialTheme.typography.bodySmall,
                    color = PamojaTextTertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = "$daysLeft days left",
                    style = MaterialTheme.typography.labelSmall,
                    color = ringColor
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Stat pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatPill(
                label    = "completed",
                value    = "${(progress * 100).toInt()}%",
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label    = "remaining",
                value    = "%,d".format(remaining),
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label    = "members",
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PamojaSurfaceVariant)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text  = value,
            style = MaterialTheme.typography.labelMedium,
            color = PamojaTextPrimary
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = PamojaTextTertiary
        )
    }
}

// ─── Leaderboard row ──────────────────────────────────────────────────────────
// Rank #1 gets a gold/amber taller card. #2+ get standard dark rows.
@Composable
fun LeaderboardRow(
    rank: Int,
    user: User,
    todaySteps: Long,
    weeklySteps: Long,
    isCurrentUser: Boolean
) {
    val isFirst = rank == 1

    val cardBg = when {
        isFirst && isCurrentUser -> Brush.linearGradient(
            listOf(Color(0xFF2D2A50), Color(0xFF1E2130)) // indigo tint for #1 current user
        )
        isFirst -> Brush.linearGradient(
            listOf(Color(0xFF2A2620), Color(0xFF1E2130)) // amber tint for #1 other
        )
        isCurrentUser -> Brush.linearGradient(
            listOf(PamojaIndigoSubtle, PamojaIndigoSubtle)
        )
        else -> Brush.linearGradient(
            listOf(PamojaSurface, PamojaSurface)
        )
    }

    val rankColor = when {
        isFirst      -> PamojaAmber
        rank == 2    -> PamojaTextSecondary
        rank == 3    -> Color(0xFFCD7F32) // bronze
        isCurrentUser -> PamojaIndigoLight
        else         -> PamojaTextTertiary
    }

    val rankLabel = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "$rank"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(if (isFirst) 18.dp else 14.dp))
            .background(brush = cardBg)
            .then(
                if (isFirst) Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                else Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Rank indicator
        if (rank <= 3) {
            Text(
                text  = rankLabel,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (isFirst) 20.sp else 16.sp
                ),
                modifier = Modifier.width(if (isFirst) 28.dp else 22.dp)
            )
        } else {
            Text(
                text  = "$rank",
                style = MaterialTheme.typography.labelMedium,
                color = rankColor,
                modifier = Modifier.width(22.dp)
            )
        }

        // Avatar circle with initials
        Box(
            modifier = Modifier
                .size(if (isFirst) 44.dp else 36.dp)
                .clip(CircleShape)
                .background(
                    brush = if (isCurrentUser)
                        Brush.linearGradient(listOf(PamojaIndigo, PamojaIndigoDark))
                    else
                        Brush.linearGradient(listOf(PamojaSurfaceHigh, PamojaSurfaceVariant))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = user.name.take(2).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (isFirst) 14.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = PamojaWhite
            )
        }

        // Name + weekly steps
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text     = user.name,
                    style    = if (isFirst)
                        MaterialTheme.typography.bodyLarge.copy(color = PamojaTextPrimary)
                    else
                        MaterialTheme.typography.bodyMedium.copy(color = PamojaTextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // "you" badge
                if (isCurrentUser) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PamojaIndigoSubtle)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text  = "you",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize   = 10.sp,
                                color      = PamojaIndigoLight
                            )
                        )
                    }
                }
            }
            Text(
                text  = "%,d this week".format(weeklySteps),
                style = MaterialTheme.typography.bodySmall.copy(color = PamojaTextSecondary)
            )
        }

        // Today's steps (right side)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text  = "%,d".format(todaySteps),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize   = if (isFirst) 15.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isFirst) PamojaAmber else PamojaTextPrimary
                )
            )
            Text(
                text  = "today",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color    = PamojaTextTertiary
                )
            )
        }
    }
}

// ─── Health connect card ──────────────────────────────────────────────────────
@Composable
fun HealthConnectCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PamojaSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PamojaIndigoSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                tint     = PamojaIndigo,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text  = "Enable step tracking",
                style = MaterialTheme.typography.bodyMedium,
                color = PamojaTextPrimary
            )
            Text(
                text  = "Tap to grant permission and start counting",
                style = MaterialTheme.typography.bodySmall,
                color = PamojaTextSecondary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint     = PamojaTextTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─── Legacy GroupHeader alias ─────────────────────────────────────────────────
// Kept so nav graph or other callers don't break
@Composable
fun GroupHeader(
    groupName: String,
    memberCount: Int,
    isAdmin: Boolean
) = GroupTopBar(groupName, memberCount, isAdmin, onBack = null)