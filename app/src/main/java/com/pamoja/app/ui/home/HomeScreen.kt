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
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.util.Calendar

// ─── Colour palette for group card avatar gradients ──────────────────────────
// Each group gets a deterministic gradient based on its name's first char, so the
// same group always shows the same colour. Decorative (white text on top), kept
// theme-independent on purpose for lively variety in both light and dark.
private val avatarGradients = listOf(
    listOf(Color(0xFF6366F1), Color(0xFF4F46E5)), // indigo
    listOf(Color(0xFF22C58B), Color(0xFF17A472)), // emerald
    listOf(Color(0xFFF5A623), Color(0xFFDB8B12)), // amber
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
    val colors = LocalPamojaColors.current
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
        joinUiState.joinedGroupId?.let { groupId ->
            // Opening a link for a group you are already in is legitimate, and
            // used to land you there with no explanation at all. Say which of
            // the two things happened before navigating.
            val message = if (joinUiState.wasAlreadyMember) {
                "You're already in this group"
            } else {
                "Joined"
            }
            joinViewModel.clearJoinedGroupId()
            snackbarHostState.showSnackbar(message)
            onGroupClick(groupId)
        }
    }

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            val hasPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    // ── Join-via-link dialog ──────────────────────────────────────────────────
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor   = colors.surface3,
            shape            = RoundedCornerShape(PamojaRadii.xl),
            title = {
                Text(
                    text  = "Join a group",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text  = "Paste the invite link shared by your group admin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(Spacing.x4))
                    OutlinedTextField(
                        value         = inviteLink,
                        onValueChange = { inviteLink = it },
                        placeholder   = {
                            Text(
                                text  = "Paste the invite link",
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
                    shape   = RoundedCornerShape(PamojaRadii.sm),
                    colors  = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Join", color = colors.textOnBrand, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
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
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color     = colors.accentPrimary,
                    modifier  = Modifier.align(Alignment.Center),
                    strokeWidth = 2.dp
                )
            } else {
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
                                color = colors.textTertiary,
                                modifier = Modifier.padding(
                                    start = Spacing.x6, end = Spacing.x6,
                                    top = Spacing.x5, bottom = Spacing.x2
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
                        Spacer(modifier = Modifier.height(Spacing.x2))
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
    val colors = LocalPamojaColors.current
    // Time-aware greeting
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else      -> "Good evening"
        }
    }

    // Rotating motivational subtitles, cycles every 4 seconds
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
            .padding(horizontal = Spacing.x6)
            .padding(top = Spacing.x4, bottom = Spacing.x1)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = "$greeting, $name",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(48.dp) // Touch target
            ) {
                Icon(
                    painter = painterResource(PamojaIcons.Settings),
                    contentDescription = "Settings",
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.x1))
        Text(
            text  = subtitles[subtitleIndex],
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
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
            text     = "Join a group via invite link",
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
    val gradientColors = gradientForGroup(group.name)
    val cardShape = RoundedCornerShape(PamojaRadii.lg)

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
        // Avatar, gradient box with first two letters
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(PamojaRadii.sm))
                .background(
                    brush = Brush.linearGradient(colors = gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = group.name.take(2).uppercase(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 16.sp,
                    color = Color.White
                )
            )
        }

        // Group info
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text(
                text     = group.name,
                style    = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = "%,d steps / week goal".format(group.weeklyTarget),
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary)
            )
        }

        // Chevron
        Icon(
            painter = painterResource(PamojaIcons.ChevronRight),
            contentDescription = "Open group",
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
            text  = "No groups yet",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary
        )
        Text(
            text      = "Create a group and invite friends or family, or join one with an invite link.",
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
                    text     = "Join",
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
                    text     = "New group",
                    style    = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}
