package com.pamoja.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.ui.theme.PamojaBorder
import com.pamoja.app.ui.theme.PamojaGreen
import com.pamoja.app.ui.theme.PamojaGreenSubtle
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaSurface
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaTextTertiary
import com.pamoja.app.ui.theme.PamojaWhite

@Composable
fun CreateOrJoinGroupScreen(
    onCreateGroup: () -> Unit,
    onJoinGroup: (String) -> Unit,
    viewModel: CreateOrJoinViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showJoinDialog    by remember { mutableStateOf(false) }
    var inviteLink        by remember { mutableStateOf("") }

    LaunchedEffect(uiState.joinedGroupId) {
        uiState.joinedGroupId?.let { onJoinGroup(it) }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // ── Join dialog ──────────────────────────────────────────────────────────
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
                        modifier   = Modifier.fillMaxWidth(),
                        shape      = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle  = MaterialTheme.typography.bodyMedium.copy(color = PamojaTextPrimary),
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
                        viewModel.joinGroup(inviteLink.trim())
                        showJoinDialog = false
                    },
                    enabled = inviteLink.isNotBlank() && !uiState.isLoading,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                text  = "Get started",
                style = MaterialTheme.typography.headlineLarge,
                color = PamojaTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text  = "Create a new group or join one your circle already started.",
                style = MaterialTheme.typography.bodyMedium,
                color = PamojaTextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            GroupOptionCard(
                icon            = Icons.Default.Add,
                iconBackground  = PamojaIndigoSubtle,
                iconTint        = PamojaIndigo,
                title           = "Create a group",
                subtitle        = "Set a goal, invite your circle",
                onClick         = onCreateGroup
            )

            Spacer(modifier = Modifier.height(12.dp))

            GroupOptionCard(
                icon            = Icons.Default.Link,
                iconBackground  = PamojaGreenSubtle,
                iconTint        = PamojaGreen,
                title           = "Join via link",
                subtitle        = "Tap a link from your group",
                onClick         = { showJoinDialog = true }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─── Option card ──────────────────────────────────────────────────────────────
@Composable
fun GroupOptionCard(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PamojaSurface)
            .border(
                width = 1.dp,
                color = PamojaBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(18.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint     = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Column {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyLarge,
                color = PamojaTextPrimary
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PamojaTextSecondary
            )
        }
    }
}