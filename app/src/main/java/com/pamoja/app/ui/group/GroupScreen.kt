package com.pamoja.app.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.domain.model.User
import com.pamoja.app.ui.theme.PamojaBlue
import com.pamoja.app.ui.theme.PamojaBlueDark
import com.pamoja.app.ui.theme.PamojaBlueLight
import com.pamoja.app.ui.theme.PamojaGreen
import com.pamoja.app.ui.theme.PamojaGreenLight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Composable
fun GroupScreen(
    groupId: String,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val userPreferences = viewModel.userPreferences
    val isHealthConnectGranted by userPreferences.isHealthConnectGranted
        .collectAsState(initial = false)

    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val today = LocalDate.now()
    val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val daysLeft = ChronoUnit.DAYS.between(today, endOfWeek).toInt() + 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = PamojaBlue
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (!isHealthConnectGranted) {
                    item {
                        HealthConnectBanner(onClick = { })
                    }
                }

                item {
                    GroupHeader(
                        groupName = uiState.group?.name ?: "",
                        memberCount = uiState.memberStepData.size,
                        isAdmin = uiState.isAdmin
                    )
                }

                item {
                    GroupProgressSection(
                        combinedSteps = uiState.combinedWeeklySteps,
                        weeklyTarget = uiState.group?.weeklyTarget?.toLong() ?: 70000L,
                        daysLeft = daysLeft,
                        memberCount = uiState.memberStepData.size
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "LEADERBOARD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                    )
                }

                itemsIndexed(uiState.memberStepData) { index, memberData ->
                    LeaderboardRow(
                        rank = index + 1,
                        user = memberData.user,
                        todaySteps = memberData.todaySteps,
                        weeklySteps = memberData.weeklySteps,
                        isCurrentUser = memberData.user.userId == uiState.currentUserId
                    )
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun GroupHeader(
    groupName: String,
    memberCount: Int,
    isAdmin: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = groupName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$memberCount members · Mon–Sun",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isAdmin) {
            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Group settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun GroupProgressSection(
    combinedSteps: Long,
    weeklyTarget: Long,
    daysLeft: Int,
    memberCount: Int
) {
    val progress = if (weeklyTarget > 0) {
        (combinedSteps.toFloat() / weeklyTarget.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val remaining = (weeklyTarget - combinedSteps).coerceAtLeast(0L)
    val dailyAvg = if (memberCount > 0) combinedSteps / memberCount else 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(180.dp)
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(180.dp),
                color = MaterialTheme.colorScheme.outline,
                strokeWidth = 12.dp,
                strokeCap = StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(180.dp),
                color = PamojaBlue,
                strokeWidth = 12.dp,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%,d".format(combinedSteps),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "of %,d steps".format(weeklyTarget),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$daysLeft days left",
                    style = MaterialTheme.typography.labelSmall,
                    color = PamojaBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatPill(
                label = "completed",
                value = "${(progress * 100).toInt()}%",
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "remaining",
                value = "%,d".format(remaining),
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "daily avg",
                value = "%,d".format(dailyAvg),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun LeaderboardRow(
    rank: Int,
    user: User,
    todaySteps: Long,
    weeklySteps: Long,
    isCurrentUser: Boolean
) {
    val backgroundColor = if (isCurrentUser) PamojaBlueLight
    else MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (isCurrentUser) PamojaBlueDark
    else MaterialTheme.colorScheme.onBackground

    val secondaryColor = if (isCurrentUser) PamojaBlue
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelMedium,
            color = secondaryColor,
            modifier = Modifier.width(16.dp)
        )

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isCurrentUser) PamojaBlue else MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.name.take(2).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontSize = 12.sp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrentUser) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PamojaBlueDark)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "you",
                            style = MaterialTheme.typography.labelSmall,
                            color = PamojaBlueLight,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Text(
                text = "%,d this week".format(weeklySteps),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "%,d".format(todaySteps),
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
            Text(
                text = "today",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun HealthConnectBanner(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PamojaBlueLight)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Connect Health to sync your steps",
            style = MaterialTheme.typography.bodyMedium,
            color = PamojaBlueDark,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClick) {
            Text(
                text = "Connect",
                style = MaterialTheme.typography.labelMedium,
                color = PamojaBlue
            )
        }
    }
}