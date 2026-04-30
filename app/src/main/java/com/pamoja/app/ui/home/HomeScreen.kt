package com.pamoja.app.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.domain.model.Group
import com.pamoja.app.ui.CreateOrJoinViewModel
import com.pamoja.app.ui.theme.PamojaBlue
import com.pamoja.app.ui.theme.PamojaBlueDark
import com.pamoja.app.ui.theme.PamojaBlueLight
import com.pamoja.app.ui.theme.PamojaGreen
import com.pamoja.app.ui.theme.PamojaGreenLight
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun HomeScreen(
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    joinViewModel: CreateOrJoinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val joinUiState by joinViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showJoinDialog by remember { mutableStateOf(false) }
    var inviteLink by remember { mutableStateOf("") }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            if (it.contains("Session expired")) {
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
            onGroupClick(groupId)
        }
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = {
                Text(
                    text = "Join a group",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column {
                    Text(
                        text = "Paste the invite link shared by your group admin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inviteLink,
                        onValueChange = { inviteLink = it },
                        placeholder = {
                            Text(
                                text = "pamoja://join/...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PamojaBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                    colors = ButtonDefaults.buttonColors(containerColor = PamojaBlue)
                ) {
                    Text("Join", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGroup,
                containerColor = PamojaBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create group"
                )
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PamojaBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 48.dp, bottom = 8.dp)
                    ) {
                        Text(
                            text = "Hey, ${uiState.userName.split(" ").firstOrNull() ?: "there"} 👋",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Here are your groups",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Join via link option
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PamojaGreenLight)
                            .clickable { showJoinDialog = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PamojaGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Join a group via invite link",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PamojaGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = PamojaGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Groups section header
                if (uiState.groups.isNotEmpty()) {
                    item {
                        Text(
                            text = "YOUR GROUPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                        )
                    }
                }

                // Group cards
                items(uiState.groups) { group ->
                    GroupCard(
                        group = group,
                        onClick = { onGroupClick(group.groupId) }
                    )
                }

                // Empty state
                if (uiState.groups.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(PamojaBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = PamojaBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = "No groups yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Create a group and invite your family or friends, or join one via a link.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun GroupCard(
    group: Group,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Avatar with first two letters of group name
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PamojaBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = group.name.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = "%,d steps / week goal".format(group.weeklyTarget),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open group",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
