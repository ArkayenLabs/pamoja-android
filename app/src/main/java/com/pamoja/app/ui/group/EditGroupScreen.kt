package com.pamoja.app.ui.group

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.domain.usecase.UpdateGroupSettingsUseCase
import com.pamoja.app.ui.components.GroupAvatar
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaConfirmDialog
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.components.rememberSingleClick
import com.pamoja.app.ui.components.toSnackbarMessage
import com.pamoja.app.ui.profile.PhotoCropScreen
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaElevation
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing
import java.time.DayOfWeek

/**
 * Admin editing for an existing group.
 *
 * Nothing about a group could be changed after it was created, so a typo in the
 * name was permanent and a goal picked before anyone had walked was stuck. This
 * covers the four settings creation asks for, plus removing a member.
 *
 * The week start day lives here rather than only at creation because it is the
 * setting most likely to be wrong: it defaults to the creating device's locale,
 * which is a guess about where the whole group is.
 */
@Composable
fun EditGroupScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditGroupViewModel = hiltViewModel(),
) {
    val colors = LocalPamojaColors.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingRemoval by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Same two-step flow as the profile photo: pick, then frame, then upload.
    var pendingCropUri by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { pendingCropUri = it.toString() } }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    pendingCropUri?.let { uri ->
        PhotoCropScreen(
            sourceUri = uri,
            onCancel = { pendingCropUri = null },
            onCropped = { cropped ->
                pendingCropUri = null
                viewModel.onPhotoPicked(cropped)
            },
        )
        return
    }

    // Failures that still leave a usable screen are a note over it, not a
    // replacement for it. A removal that failed must not discard an edit in
    // progress.
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        if (uiState.group != null) {
            snackbarHostState.showSnackbar(error.toSnackbarMessage(context))
            viewModel.clearError()
        }
    }

    pendingRemoval?.let { (memberId, memberName) ->
        PamojaConfirmDialog(
            title = stringResource(R.string.edit_group_remove_title),
            body = stringResource(R.string.edit_group_remove_body, memberName),
            confirmLabel = stringResource(R.string.edit_group_remove_confirm),
            onConfirm = {
                pendingRemoval = null
                viewModel.removeMember(memberId)
            },
            onDismiss = { pendingRemoval = null },
        )
    }

    Scaffold(
        containerColor = colors.surfaceApp,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceApp),
        ) {
            when {
                // Full-screen error only when there is nothing else to show.
                uiState.group == null && uiState.error != null -> {
                    PamojaErrorState(
                        error = uiState.error!!,
                        onRetry = viewModel::retry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                !uiState.hasLoadedOnce -> EditGroupSkeleton()

                else -> EditGroupContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = onBack,
                    onRequestRemove = { id, name -> pendingRemoval = id to name },
                    photoPicker = photoPicker,
                )
            }
        }
    }
}

@Composable
private fun EditGroupContent(
    uiState: EditGroupUiState,
    viewModel: EditGroupViewModel,
    onBack: () -> Unit,
    onRequestRemove: (String, String) -> Unit,
    photoPicker: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
) {
    val colors = LocalPamojaColors.current
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(PamojaIcons.ArrowLeft),
                    contentDescription = stringResource(R.string.common_back),
                    tint = colors.textPrimary,
                )
            }
            Spacer(Modifier.width(Spacing.x2))
            Text(
                text = stringResource(R.string.edit_group_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
        }

        OfflineBanner(isOffline = uiState.isOffline)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            // Someone who is not the admin should never reach this screen, but
            // saying so beats a screen of controls that all fail on save.
            if (!uiState.isAdmin) {
                EditCard {
                    Text(
                        text = stringResource(R.string.edit_group_not_admin),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            // ── Photo ────────────────────────────────────────────────────
            //
            // The same cropper the profile photo uses. A group avatar is drawn
            // as a squircle rather than a circle, but the crop is square either
            // way, so there is nothing to specialise.
            EditCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        GroupAvatar(
                            name = uiState.name.ifBlank { uiState.group?.name.orEmpty() },
                            size = 64.dp,
                            photoUrl = uiState.photoUrl,
                        )
                        if (uiState.isUploadingPhoto) {
                            CircularProgressIndicator(
                                color = colors.accentPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(Spacing.x4))

                    Column(modifier = Modifier.weight(1f)) {
                        CardHeading(
                            title = stringResource(R.string.edit_group_photo),
                            subtitle = stringResource(R.string.edit_group_photo_sub),
                        )
                        Spacer(Modifier.height(Spacing.x3))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                            Button(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                enabled = uiState.isAdmin &&
                                    !uiState.isUploadingPhoto &&
                                    !uiState.isOffline,
                                shape = PillShape,
                                contentPadding = PaddingValues(
                                    horizontal = Spacing.x4,
                                    vertical = Spacing.x2,
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentPrimarySubtle,
                                    contentColor = colors.accentPrimary,
                                ),
                            ) {
                                Text(
                                    text = stringResource(
                                        if (uiState.photoUrl.isBlank())
                                            R.string.edit_group_photo_add
                                        else
                                            R.string.edit_group_photo_change
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }

                            if (uiState.photoUrl.isNotBlank() && uiState.isAdmin) {
                                TextButton(
                                    onClick = viewModel::onPhotoRemoved,
                                    enabled = !uiState.isUploadingPhoto && !uiState.isOffline,
                                ) {
                                    Text(
                                        text = stringResource(R.string.edit_group_photo_remove),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.statusDanger,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            EditCard {
                PamojaTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = stringResource(R.string.edit_group_name_label),
                    placeholder = stringResource(R.string.edit_group_name_placeholder),
                    enabled = uiState.isAdmin,
                )
            }

            // ── Weekly goal ──────────────────────────────────────────────
            EditCard {
                CardHeading(
                    title = stringResource(R.string.edit_group_goal),
                    subtitle = stringResource(R.string.edit_group_goal_sub),
                )
                Spacer(Modifier.height(Spacing.x3))
                Text(
                    text = stringResource(
                        R.string.edit_group_goal_value,
                        "%,d".format(uiState.weeklyTarget),
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.accentPrimary,
                )
                Slider(
                    value = uiState.weeklyTarget.toFloat(),
                    onValueChange = {
                        // Rounded to the nearest step so the number reads as a
                        // goal rather than a sensor reading.
                        viewModel.onWeeklyTargetChange(
                            (it / GoalStep).toInt() * GoalStep
                        )
                    },
                    valueRange = GoalMin.toFloat()..GoalMax.toFloat(),
                    enabled = uiState.isAdmin,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accentPrimary,
                        activeTrackColor = colors.accentPrimary,
                        inactiveTrackColor = colors.surface2,
                    ),
                )
            }

            // ── Member cap ───────────────────────────────────────────────
            EditCard {
                CardHeading(
                    title = stringResource(R.string.edit_group_cap),
                    subtitle = stringResource(R.string.edit_group_cap_sub),
                )
                Spacer(Modifier.height(Spacing.x3))
                Text(
                    // Plural, not a format string. A count followed by a noun
                    // is exactly the shape that produced "1 members" once.
                    text = pluralStringResource(
                        R.plurals.member_count,
                        uiState.maxMemberCap,
                        uiState.maxMemberCap,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.accentPrimary,
                )
                Slider(
                    value = uiState.maxMemberCap.toFloat(),
                    onValueChange = { viewModel.onMemberCapChange(it.toInt()) },
                    valueRange = uiState.minSelectableCap.toFloat()..
                        UpdateGroupSettingsUseCase.MAX_MEMBER_CAP.toFloat(),
                    enabled = uiState.isAdmin,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accentPrimary,
                        activeTrackColor = colors.accentPrimary,
                        inactiveTrackColor = colors.surface2,
                    ),
                )
                // Explains why the slider stops where it does, rather than
                // leaving it feeling broken.
                if (uiState.minSelectableCap > UpdateGroupSettingsUseCase.MIN_MEMBER_CAP) {
                    Text(
                        text = stringResource(
                            R.string.edit_group_cap_floor,
                            uiState.minSelectableCap,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }

            // ── Week start day ───────────────────────────────────────────
            EditCard {
                CardHeading(
                    title = stringResource(R.string.create_group_week_start),
                    subtitle = stringResource(R.string.edit_group_week_start_sub),
                )
                Spacer(Modifier.height(Spacing.x3))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                ) {
                    viewModel.weekStartOptions.forEach { day ->
                        val selected = uiState.weekStartDay == day
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PillShape)
                                .background(
                                    if (selected) colors.accentPrimarySubtle else colors.surface2
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) colors.accentPrimary
                                            else colors.borderSubtle,
                                    shape = PillShape,
                                )
                                .clickable(enabled = uiState.isAdmin) {
                                    viewModel.onWeekStartDayChange(day)
                                }
                                .padding(vertical = Spacing.x3),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(
                                    if (day == DayOfWeek.MONDAY)
                                        R.string.create_group_week_start_monday
                                    else
                                        R.string.create_group_week_start_sunday
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) colors.accentPrimary
                                        else colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // ── Members can edit the goal ────────────────────────────────
            EditCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CardHeading(
                            title = stringResource(R.string.create_group_members_edit),
                            subtitle = stringResource(R.string.edit_group_members_edit_sub),
                        )
                    }
                    Spacer(Modifier.width(Spacing.x3))
                    Switch(
                        checked = uiState.canMembersEditTarget,
                        onCheckedChange = viewModel::onMembersEditTargetChange,
                        enabled = uiState.isAdmin,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accentPrimary,
                            checkedBorderColor = colors.accentPrimary,
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.surface2,
                            uncheckedBorderColor = colors.borderDefault,
                        ),
                    )
                }
            }

            // ── Members ──────────────────────────────────────────────────
            EditCard {
                CardHeading(
                    title = stringResource(R.string.edit_group_members),
                    subtitle = stringResource(R.string.edit_group_members_sub),
                )
                Spacer(Modifier.height(Spacing.x3))

                uiState.members.forEach { member ->
                    val isGroupAdmin = member.userId == uiState.group?.adminId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.x2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            if (isGroupAdmin) {
                                Text(
                                    text = stringResource(R.string.edit_group_member_admin),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textTertiary,
                                )
                            }
                        }

                        when {
                            // The admin has no Remove of their own. Leaving is a
                            // different action with a different consequence.
                            isGroupAdmin -> Unit

                            uiState.removingMemberId == member.userId -> {
                                CircularProgressIndicator(
                                    color = colors.accentPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            uiState.isAdmin -> {
                                IconButton(
                                    onClick = {
                                        onRequestRemove(member.userId, member.name)
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(PamojaIcons.Trash),
                                        contentDescription = stringResource(
                                            R.string.edit_group_remove_confirm
                                        ),
                                        tint = colors.statusDanger,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.x4))
        }

        // ── Save ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
        ) {
            Button(
                onClick = rememberSingleClick { viewModel.save() },
                enabled = uiState.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                    disabledContainerColor = colors.surface2,
                    disabledContentColor = colors.textTertiary,
                ),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = colors.textOnBrand,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.edit_group_save),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** Matches the real layout, so nothing jumps when the group arrives. */
@Composable
private fun EditGroupSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        SkeletonBlock(modifier = Modifier.width(160.dp).height(28.dp))
        Spacer(Modifier.height(Spacing.x2))
        repeat(4) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(96.dp))
        }
    }
}

@Composable
private fun CardHeading(title: String, subtitle: String) {
    val colors = LocalPamojaColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = colors.textPrimary,
    )
    Spacer(Modifier.height(Spacing.x1))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
    )
}

@Composable
private fun EditCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalPamojaColors.current
    val cardShape = RoundedCornerShape(PamojaRadii.xl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(PamojaElevation.card, cardShape, spotColor = colors.overlay)
            .clip(cardShape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
        content = content,
    )
}

private const val GoalMin = 10_000
private const val GoalMax = 500_000
private const val GoalStep = 5_000
