package com.pamoja.app.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.ui.onboarding.DarkTextField
import com.pamoja.app.ui.theme.PamojaBackground
import com.pamoja.app.ui.theme.PamojaBorder
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaSurface
import com.pamoja.app.ui.theme.PamojaSurfaceVariant
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaTextTertiary
import com.pamoja.app.ui.theme.PamojaWhite

@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var groupName          by remember { mutableStateOf("") }
    var weeklyTargetIndex  by remember { mutableFloatStateOf(2f) }
    var maxMembers         by remember { mutableFloatStateOf(10f) }
    var canMembersEdit     by remember { mutableStateOf(true) }

    val stepPresets      = listOf(35_000, 50_000, 70_000, 100_000, 150_000)
    val stepPresetLabels = listOf("35k", "50k", "70k", "100k", "150k")
    val selectedTarget   = stepPresets[weeklyTargetIndex.toInt()]

    LaunchedEffect(uiState.createdGroupId) {
        uiState.createdGroupId?.let { onGroupCreated(it) }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PamojaBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Top bar ────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = PamojaTextSecondary,
                        modifier           = Modifier.size(20.dp)
                    )
                }
                Text(
                    text  = "Back",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PamojaTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text  = "Create your group",
                style = MaterialTheme.typography.headlineLarge,
                color = PamojaTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text  = "You can edit these anytime as admin.",
                style = MaterialTheme.typography.bodyMedium,
                color = PamojaTextSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Group name ─────────────────────────────────────────────
            DarkTextField(
                value         = groupName,
                onValueChange = { groupName = it },
                label         = "Group name",
                placeholder   = "e.g. Sharma Family"
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Weekly step goal ───────────────────────────────────────
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "Weekly step goal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PamojaTextPrimary
                    )
                    Text(
                        text  = "%,d steps".format(selectedTarget),
                        style = MaterialTheme.typography.labelMedium,
                        color = PamojaIndigo
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value         = weeklyTargetIndex,
                    onValueChange = { weeklyTargetIndex = it },
                    valueRange    = 0f..4f,
                    steps         = 3,
                    colors = SliderDefaults.colors(
                        thumbColor          = PamojaIndigo,
                        activeTrackColor    = PamojaIndigo,
                        inactiveTrackColor  = PamojaSurfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    stepPresetLabels.forEach { label ->
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = PamojaTextTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Max members ────────────────────────────────────────────
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "Max members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PamojaTextPrimary
                    )
                    Text(
                        text  = "${maxMembers.toInt()} members",
                        style = MaterialTheme.typography.labelMedium,
                        color = PamojaIndigo
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value         = maxMembers,
                    onValueChange = { maxMembers = it },
                    valueRange    = 2f..20f,
                    steps         = 17,
                    colors = SliderDefaults.colors(
                        thumbColor         = PamojaIndigo,
                        activeTrackColor   = PamojaIndigo,
                        inactiveTrackColor = PamojaSurfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("2",  style = MaterialTheme.typography.labelSmall, color = PamojaTextTertiary)
                    Text("20", style = MaterialTheme.typography.labelSmall, color = PamojaTextTertiary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Members can edit goal toggle ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PamojaSurface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "Members can edit goal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PamojaTextPrimary
                    )
                    Text(
                        text  = "Allow others to change the step target",
                        style = MaterialTheme.typography.bodySmall,
                        color = PamojaTextSecondary
                    )
                }
                Switch(
                    checked         = canMembersEdit,
                    onCheckedChange = { canMembersEdit = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = PamojaWhite,
                        checkedTrackColor   = PamojaIndigo,
                        uncheckedThumbColor = PamojaTextSecondary,
                        uncheckedTrackColor = PamojaSurfaceVariant,
                        uncheckedBorderColor = PamojaBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.createGroup(
                        name                = groupName.trim(),
                        weeklyTarget        = selectedTarget,
                        maxMemberCap        = maxMembers.toInt(),
                        canMembersEditTarget = canMembersEdit
                    )
                },
                enabled  = groupName.isNotBlank() && !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = PamojaIndigo,
                    contentColor           = PamojaWhite,
                    disabledContainerColor = PamojaIndigoSubtle,
                    disabledContentColor   = PamojaTextTertiary
                )
            ) {
                Text(
                    text  = if (uiState.isLoading) "Creating…" else "Create group",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(40.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─── Setting card container ────────────────────────────────────────────────────
// Wraps slider controls in a dark surface card for visual grouping
@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PamojaSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}