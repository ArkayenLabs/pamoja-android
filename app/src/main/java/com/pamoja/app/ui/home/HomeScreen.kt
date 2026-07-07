package com.pamoja.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.domain.model.Group
import com.pamoja.app.ui.CreateOrJoinViewModel
import com.pamoja.app.ui.theme.PamojaAmber
import com.pamoja.app.ui.theme.PamojaBackground
import com.pamoja.app.ui.theme.PamojaBorder
import com.pamoja.app.ui.theme.PamojaGreen
import com.pamoja.app.ui.theme.PamojaGreenSubtle
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoDark
import com.pamoja.app.ui.theme.PamojaIndigoLight
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaSurface
import com.pamoja.app.ui.theme.PamojaSurfaceVariant
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaTextTertiary
import com.pamoja.app.ui.theme.PamojaWhite
import kotlinx.coroutines.delay
import java.util.Calendar

// ─── Colour palette for group card avatar gradients ──────────────────────────
// Each group gets a deterministic gradient based on its name's first char,
// so the same group always shows the same colour — not random each time.
private val avatarGradients = listOf(
    listOf(Color(0xFF6366F1), Color(0xFF4F46E5)), // indigo
    listOf(Color(0xFF34D399), Color(0xFF059669)), // emerald
    listOf(Color(0xFFF59E0B), Color(0xFFD97706)), // amber
    listOf(Color(0xFFF87171), Color(0xFFEF4444)), // rose
    listOf(Color(0xFF60A5FA), Color(0xFF2563EB)), // blue
    listOf(Color(0xFFC084FC), Color(0xFF9333EA)), // purple
)

private fun gradientForGroup(name: String): List<Color> {
    val index = (name.firstOrNull()?.code ?: 0) % avatarGradients.size
    return avatarGradients[index]
}

@Composable
fun HomeScreen(
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onSettingsClick: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    joinViewModel: CreateOrJoinViewModel = hiltViewModel()
) {
    val uiState     by viewModel.uiState.collectAsState()
    val joinUiState by joinViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showJoinDialog by remember { mutableStateOf(false) }
    var inviteLink     by remember { mutableStateOf("") }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            if (it.contains("Session expired")) {
                viewModel.clearError()
                onSessionExpired()
            } else {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }
    LaunchedEffect(joinUiState.error) {
        joinUiState.error?.let {
            snackbarHostState.showSnackbar(it)
            joinViewModel.clearError()
        }
    }
    LaunchedEffect(joinUiState.joinedGroupId) {
        joinUiState.joinedGroupId?.let {
            joinViewModel.clearJoinedGroupId()
            onGroupClick(it)
        }
    }

    // ── Join-via-link dialog ──────────────────────────────────────────────────
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor   = PamojaSurface,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text  = "Join a group",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PamojaTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text  = "Paste the invite link shared by your group admin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PamojaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value         = inviteLink,
                        onValueChange = { inviteLink = it },
                        placeholder   = {
                            Text(
                                text  = "pamoja://join/…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PamojaTextTertiary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = PamojaTextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = PamojaIndigo,
                            unfocusedBorderColor    = PamojaBorder,
                            focusedContainerColor   = PamojaSurface,
                            unfocusedContainerColor = PamojaSurface,
                            cursorColor             = PamojaIndigo
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        joinViewModel.joinGroup(inviteLink.trim())
                        showJoinDialog = false
                        inviteLink = ""
                    },
                    enabled = inviteLink.isNotBlank() && !joinUiState.isLoading,
                    shape   = RoundedCornerShape(12.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = PamojaIndigo)
                ) {
                    Text("Join", color = PamojaWhite, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel", color = PamojaTextSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = PamojaBackground,
        snackbarHost   = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                // ── Loading state ─────────────────────────────────────────
                CircularProgressIndicator(
                    color     = PamojaIndigo,
                    modifier  = Modifier.align(Alignment.Center),
                    strokeWidth = 2.dp
                )
            } else {
                // ── Main scrollable content ───────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Greeting header
                    item {
                        HomeHeader(
                            name = uiState.userName
                                .takeIf { it.isNotBlank() }
                                ?.split(" ")
                                ?.firstOrNull() ?: "there",
                            onSettingsClick = onSettingsClick
                        )
                    }

                    // Section label
                    if (uiState.groups.isNotEmpty()) {
                        item {
                            Text(
                                text  = "YOUR GROUPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = PamojaTextTertiary,
                                modifier = Modifier.padding(
                                    start = 24.dp, end = 24.dp,
                                    top = 20.dp, bottom = 8.dp
                                )
                            )
                        }
                    }

                    // Group cards
                    items(uiState.groups) { group ->
                        GroupCard(
                            group   = group,
                            onClick = { onGroupClick(group.groupId) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Empty state
                    if (uiState.groups.isEmpty()) {
                        item {
                            EmptyGroupsState()
                        }
                    }

                    item { Spacer(modifier = Modifier.height(120.dp)) }
                }

                // ── Floating bottom action bar ────────────────────────────
                // Anchored to bottom instead of a FAB so it feels more iOS-native
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
    onSettingsClick: () -> Unit
) {
    // Time-aware greeting
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else      -> "Good evening"
        }
    }

    // Rotating motivational subtitles — cycles every 4 seconds
    val subtitles = remember {
        listOf(
            "Every step counts. Let's go!",
            "Your group is counting on you.",
            "Walk together, grow together.",
            "Today's a great day to move.",
            "Small steps. Big impact."
        )
    }
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
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = "$greeting, $name",
                style = MaterialTheme.typography.headlineLarge,
                color = PamojaTextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(48.dp) // Touch target
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = PamojaIndigoLight,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = subtitles[subtitleIndex],
            style = MaterialTheme.typography.bodyMedium,
            color = PamojaTextSecondary
        )
    }
}

// ─── Join via link card ───────────────────────────────────────────────────────
@Composable
private fun JoinLinkCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PamojaGreenSubtle)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PamojaGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint     = PamojaGreen,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text     = "Join a group via invite link",
            style    = MaterialTheme.typography.bodyMedium,
            color    = PamojaGreen,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint     = PamojaGreen,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─── Group card ───────────────────────────────────────────────────────────────
@Composable
fun GroupCard(group: Group, onClick: () -> Unit) {
    val gradientColors = gradientForGroup(group.name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PamojaSurface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Avatar — gradient box with first two letters
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    brush = Brush.linearGradient(colors = gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = group.name.take(2).uppercase(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 16.sp,
                    color = PamojaWhite
                )
            )
        }

        // Group info
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text     = group.name,
                style    = MaterialTheme.typography.bodyLarge.copy(color = PamojaTextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = "%,d steps / week goal".format(group.weeklyTarget),
                style = MaterialTheme.typography.bodySmall.copy(color = PamojaTextSecondary)
            )
        }

        // Chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open group",
            tint     = PamojaTextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────
@Composable
private fun EmptyGroupsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(PamojaIndigoSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint     = PamojaIndigo,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = "No groups yet",
            style = MaterialTheme.typography.headlineSmall,
            color = PamojaTextPrimary
        )
        Text(
            text      = "Create a group and invite friends or family, or join one with an invite link.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = PamojaTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Bottom action bar ────────────────────────────────────────────────────────
// Two equal buttons side by side, floating above nav bar.
// More ergonomic than a single FAB — clearer call to action.
@Composable
private fun BottomActionBar(
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Gradient scrim behind the buttons so they're never transparent over content
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to PamojaBackground.copy(alpha = 0f),
                        0.25f to PamojaBackground
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Join (secondary)
            Button(
                onClick  = onJoinGroup,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PamojaSurfaceVariant,
                    contentColor   = PamojaTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = PamojaTextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text     = "Join",
                    style    = MaterialTheme.typography.labelLarge.copy(color = PamojaTextPrimary),
                    maxLines = 1
                )
            }

            // Create (primary) — no leading icon so text never wraps on narrow screens
            Button(
                onClick  = onCreateGroup,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PamojaIndigo,
                    contentColor   = PamojaWhite
                )
            ) {
                Text(
                    text     = "+ New group",
                    style    = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}
