package com.pamoja.app.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val uiState           by viewModel.uiState.collectAsState()

    var groupName          by remember { mutableStateOf("") }
    var weeklyTargetIndex  by remember { mutableFloatStateOf(2f) }
    var maxMembers         by remember { mutableFloatStateOf(10f) }
    var canMembersEdit     by remember { mutableStateOf(true) }

    // Hoisted: validate runs outside composable scope.
    val nameRequired = stringResource(R.string.create_group_name_required)
    val nameTooLong = stringResource(R.string.create_group_name_too_long)

    // One rule, two callers: the field checks it on blur, the button checks it
    // every recomposition. Previously the button only asked isNotBlank(), so a
    // name of pure spaces passed the gate and arrived at Firestore trimmed to "".
    val nameErrorFor: (String) -> String? = { input ->
        when {
            input.isBlank() -> nameRequired
            input.trim().length > 50 -> nameTooLong
            else -> null
        }
    }
    val nameError = nameErrorFor(groupName)

    val stepPresets      = listOf(35_000, 50_000, 70_000, 100_000, 150_000)
    val stepPresetLabels = listOf("35k", "50k", "70k", "100k", "150k")
    val selectedTarget   = stepPresets[weeklyTargetIndex.toInt()]

    LaunchedEffect(uiState.createdGroupId) {
        uiState.createdGroupId?.let {
            viewModel.clearCreatedGroupId()
            onGroupCreated(it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
        // Outside the scroll region, so the condition stays on screen rather
        // than scrolling away while the user is still offline.
        OfflineBanner(isOffline = uiState.isOffline)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.x6)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(Spacing.x4))

            // ── Top bar ────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.x1)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter            = painterResource(PamojaIcons.ArrowLeft),
                        contentDescription = stringResource(R.string.common_back),
                        tint               = colors.textSecondary,
                        modifier           = Modifier.size(20.dp)
                    )
                }
                Text(
                    text  = stringResource(R.string.common_back),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x6))

            Text(
                text  = stringResource(R.string.create_group_title),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(Spacing.x2))
            Text(
                text  = stringResource(R.string.create_group_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(Spacing.x7))

            // ── Group name ─────────────────────────────────────────────
            PamojaTextField(
                value         = groupName,
                onValueChange = { groupName = it },
                label         = stringResource(R.string.create_group_name_label),
                placeholder   = stringResource(R.string.create_group_name_placeholder),
                enabled       = !uiState.isLoading,
                validate      = nameErrorFor,
            )

            Spacer(modifier = Modifier.height(Spacing.x7))

            // ── Weekly step goal ───────────────────────────────────────
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = stringResource(R.string.create_group_weekly_goal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text  = stringResource(
                            R.string.create_group_steps_format,
                            "%,d".format(selectedTarget)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentPrimary
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.x3))

                Slider(
                    value         = weeklyTargetIndex,
                    onValueChange = { weeklyTargetIndex = it },
                    valueRange    = 0f..4f,
                    steps         = 3,
                    colors = SliderDefaults.colors(
                        thumbColor          = colors.accentPrimary,
                        activeTrackColor    = colors.accentPrimary,
                        inactiveTrackColor  = colors.accentPrimarySubtle
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
                            color = colors.textTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x3))

            // ── Max members ────────────────────────────────────────────
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = stringResource(R.string.create_group_max_members),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text  = pluralStringResource(
                            R.plurals.member_count,
                            maxMembers.toInt(),
                            maxMembers.toInt()
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentPrimary
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.x3))

                Slider(
                    value         = maxMembers,
                    onValueChange = { maxMembers = it },
                    valueRange    = 2f..20f,
                    steps         = 17,
                    colors = SliderDefaults.colors(
                        thumbColor         = colors.accentPrimary,
                        activeTrackColor   = colors.accentPrimary,
                        inactiveTrackColor = colors.accentPrimarySubtle
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("2",  style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                    Text("20", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x3))

            // ── Members can edit goal toggle ───────────────────────────
            val toggleShape = RoundedCornerShape(PamojaRadii.md)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(toggleShape)
                    .background(colors.surface1)
                    .border(1.dp, colors.borderSubtle, toggleShape)
                    .padding(horizontal = Spacing.x4, vertical = Spacing.x4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = stringResource(R.string.create_group_members_edit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text  = stringResource(R.string.create_group_members_edit_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                Switch(
                    checked         = canMembersEdit,
                    onCheckedChange = { canMembersEdit = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor    = colors.textOnBrand,
                        checkedTrackColor    = colors.accentPrimary,
                        uncheckedThumbColor  = colors.textSecondary,
                        uncheckedTrackColor  = colors.surfaceSunken,
                        uncheckedBorderColor = colors.borderDefault
                    )
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x6))

            // Both of these sit next to the button they block, and both persist.
            // A snackbar used to carry the failure and then vanish, leaving a
            // form that had silently done nothing.
            if (uiState.isOffline) {
                PamojaNotice(
                    icon  = PamojaIcons.AlertCircle,
                    title = stringResource(R.string.create_group_offline_title),
                    body  = stringResource(R.string.create_group_offline_body),
                    tone  = NoticeTone.Warning,
                )
                Spacer(modifier = Modifier.height(Spacing.x3))
            } else if (uiState.error != null) {
                PamojaNotice(
                    icon  = PamojaIcons.AlertCircle,
                    title = stringResource(R.string.create_group_failed_title),
                    body  = uiState.error!!.toErrorCopy().body(LocalContext.current),
                    tone  = NoticeTone.Danger,
                )
                Spacer(modifier = Modifier.height(Spacing.x3))
            }

            Button(
                onClick = {
                    viewModel.createGroup(
                        name                = groupName.trim(),
                        weeklyTarget        = selectedTarget,
                        maxMemberCap        = maxMembers.toInt(),
                        canMembersEditTarget = canMembersEdit
                    )
                },
                enabled  = nameError == null && uiState.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = colors.accentPrimary,
                    contentColor           = colors.textOnBrand,
                    disabledContainerColor = colors.accentPrimarySubtle,
                    disabledContentColor   = colors.textTertiary
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        color       = colors.textTertiary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                }
                Text(
                    text  = stringResource(if (uiState.isLoading) R.string.create_group_creating else R.string.create_group_submit),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Why the button is dead. Only once they have actually typed
            // something, since nagging an untouched empty form is just noise.
            if (groupName.isNotEmpty() && nameError != null) {
                Spacer(modifier = Modifier.height(Spacing.x2))
                Text(
                    text  = nameError,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.statusDanger,
                )
            }

            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(Spacing.x10)
            )
        }
        }
    }
}

// ─── Setting card container ────────────────────────────────────────────────────
@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalPamojaColors.current
    val cardShape = RoundedCornerShape(PamojaRadii.md)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x4),
        content = content
    )
}
