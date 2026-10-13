package com.pamoja.app.ui.group

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.StepGoal
import com.pamoja.app.domain.usecase.UpdateGroupSettingsUseCase
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.components.toSnackbarMessage
import com.pamoja.app.ui.kit.ActionRow
import com.pamoja.app.ui.kit.CardHeader
import com.pamoja.app.ui.kit.CardTone
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.GroupTile
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitDialog
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSheet
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.ListRow
import com.pamoja.app.ui.kit.OptionRow
import com.pamoja.app.ui.kit.PersonRow
import com.pamoja.app.ui.kit.PopChip
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.profile.PhotoCropScreen
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.util.BillingIdentity
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * Group details (REBUILD_PLAN.md §6): members, the admin's settings, Premium,
 * and leaving. Reached from the button beside the group's name on Home.
 */
@Composable
fun GroupDetailsScreen(
    onBack: () -> Unit,
    onInvite: (String) -> Unit,
    onOpenWeeklyCard: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenPaywall: () -> Unit,
    /** Left or deleted the group: back to Home, which moves to another group. */
    onGone: () -> Unit,
    viewModel: GroupDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingCropUri by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { pendingCropUri = it.toString() }
    }

    LaunchedEffect(uiState.isGone) { if (uiState.isGone) onGone() }

    // Pick, frame, then upload, as for the profile photo.
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

    GroupDetailsContent(
        state = uiState,
        onBack = onBack,
        onInvite = { uiState.group?.let { onInvite(it.groupId) } },
        onOpenWeeklyCard = onOpenWeeklyCard,
        onOpenReview = onOpenReview,
        onOpenPaywall = onOpenPaywall,
        onSaveName = viewModel::saveName,
        onSaveGoal = viewModel::saveWeeklyTarget,
        onSaveCap = viewModel::saveMemberCap,
        onSaveWeekStart = viewModel::saveWeekStart,
        onSettingsSavedShown = viewModel::consumeSettingsSaved,
        onChoosePhoto = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemovePhoto = viewModel::removePhoto,
        onRemoveMember = viewModel::removeMember,
        onLeave = viewModel::leaveGroup,
        onDelete = viewModel::deleteGroup,
        onRetry = viewModel::retry,
        onActionErrorShown = viewModel::clearActionError,
    )
}

private enum class DetailSheet { Name, Goal, Cap, WeekStart, Photo }

/** Stateless rendering boundary, driven directly by tests. */
@Composable
internal fun GroupDetailsContent(
    state: GroupDetailsUiState,
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenPaywall: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveGoal: (Int) -> Unit,
    onSaveCap: (Int) -> Unit,
    onSaveWeekStart: (DayOfWeek) -> Unit,
    onSettingsSavedShown: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onActionErrorShown: () -> Unit,
    subscriptionSalesEnabled: Boolean = BillingIdentity.isSalesEnabled,
    onOpenWeeklyCard: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheet by rememberSaveable { mutableStateOf<DetailSheet?>(null) }
    var memberSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            snackbar.showSnackbar(it.toSnackbarMessage(context))
            onActionErrorShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        KitScreen(
            gutter = 20.dp,
            top = {
                TopRow(start = {
                    CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
                })
            },
        ) {
            val group = state.group
            when {
                state.isGroupUnavailable -> {
                    KitNotice(
                        title = stringResource(R.string.group_unavailable_title),
                        body = stringResource(R.string.group_unavailable_body),
                    )
                    SecondaryButton(stringResource(R.string.common_back), onClick = onBack)
                }

                group == null && state.fatalError != null -> {
                    val copy = state.fatalError.toErrorCopy()
                    KitNotice(title = stringResource(copy.title), body = copy.body(context), danger = true)
                    PrimaryButton(stringResource(R.string.common_try_again), onClick = onRetry)
                }

                group == null -> {
                    KitSkeleton(height = 72.dp)
                    KitSkeleton(height = 320.dp)
                    KitSkeleton(height = 220.dp)
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GroupTile(photoUrl = group.photoUrl, size = 72.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(group.name, style = Kit.type.headline.copy(fontSize = 30.sp))
                            Text(
                                stringResource(
                                    R.string.details_subtitle,
                                    pluralStringResource(R.plurals.member_count, group.memberCount, group.memberCount),
                                    group.startDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                                ),
                                style = Kit.type.caption.copy(fontSize = 14.sp),
                                color = Kit.colors.muted,
                            )
                        }
                    }

                    if (state.isOffline) {
                        KitNotice(
                            title = stringResource(R.string.offline_banner_title),
                            body = stringResource(R.string.offline_group_stale),
                        )
                    }

                    // Invite and the weekly card side by side, as drawn. A full
                    // group has no room to invite into, so the card stands alone.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!state.isFull) {
                            val inactive = stringResource(R.string.group_invite_inactive)
                            PrimaryButton(
                                stringResource(R.string.details_invite),
                                icon = PamojaIcons.Add,
                                onClick = {
                                    if (group.inviteLinkActive) onInvite() else scope.launch { snackbar.showSnackbar(inactive) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SecondaryButton(
                            stringResource(R.string.details_weekly_card),
                            onClick = onOpenWeeklyCard,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Out in the open, not inside Premium: last week is free to
                    // everyone, and older weeks are marked inside the review.
                    KitCard(padding = 6.dp, spacing = 0.dp) {
                        ListRow(
                            stringResource(R.string.details_past_weeks),
                            onClick = onOpenReview,
                            showDivider = false,
                        )
                    }

                    MembersCard(
                        state = state,
                        onMemberOptions = { memberSheet = it },
                    )

                    if (state.isAdmin) {
                        KitCard(spacing = 0.dp, padding = 18.dp) {
                            CardHeader(stringResource(R.string.details_settings_title), Modifier.padding(bottom = 6.dp))
                            ListRow(stringResource(R.string.edit_group_name_label), value = group.name, onClick = { sheet = DetailSheet.Name })
                            ListRow(
                                stringResource(R.string.details_goal),
                                value = stringResource(R.string.details_goal_value, "%,d".format(group.weeklyTarget)),
                                onClick = { sheet = DetailSheet.Goal },
                            )
                            ListRow(
                                stringResource(R.string.details_member_limit),
                                value = group.maxMemberCap.toString(),
                                onClick = { sheet = DetailSheet.Cap },
                            )
                            ListRow(
                                stringResource(R.string.details_week_starts),
                                value = group.startDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                                onClick = { sheet = DetailSheet.WeekStart },
                            )
                            ListRow(
                                stringResource(R.string.edit_group_photo),
                                value = stringResource(
                                    if (group.photoUrl.isBlank()) R.string.details_photo_add else R.string.edit_group_photo_change,
                                ),
                                onClick = { sheet = DetailSheet.Photo },
                                showDivider = false,
                            )
                        }
                    }

                    PremiumCard(
                        access = state.access,
                        shields = group.streakShields,
                        salesEnabled = subscriptionSalesEnabled,
                        onOpenReview = onOpenReview,
                        onOpenPaywall = onOpenPaywall,
                    )

                    if (state.isAdmin) {
                        QuietButton(
                            stringResource(R.string.edit_group_delete),
                            onClick = { confirmDelete = true },
                            danger = true,
                            enabled = !state.isOffline,
                        )
                    } else {
                        QuietButton(
                            stringResource(R.string.group_leave_title),
                            onClick = { confirmLeave = true },
                            danger = true,
                            enabled = !state.isOffline,
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val group = state.group ?: return

    sheet?.let { which ->
        SettingSheet(
            which = which,
            state = state,
            onDismiss = { sheet = null },
            onSaveName = onSaveName,
            onSaveGoal = onSaveGoal,
            onSaveCap = onSaveCap,
            onSaveWeekStart = onSaveWeekStart,
            onSettingsSavedShown = onSettingsSavedShown,
            onChoosePhoto = onChoosePhoto,
            onRemovePhoto = onRemovePhoto,
        )
    }

    // Gone from the list (left, or removed elsewhere) means nothing to act on.
    memberSheet?.let { memberId -> state.members.firstOrNull { it.userId == memberId } }?.let { member ->
        KitSheet(onDismiss = { memberSheet = null }, title = member.displayName) { close ->
            ActionRow(
                stringResource(R.string.details_remove_member),
                icon = PamojaIcons.Trash,
                danger = true,
                enabled = !state.isOffline,
                onClick = { close { confirmRemove = member.userId } },
            )
        }
    }

    confirmRemove?.let { memberId ->
        val name = state.members.firstOrNull { it.userId == memberId }?.displayName.orEmpty()
        KitDialog(
            title = stringResource(R.string.edit_group_remove_title),
            body = stringResource(R.string.edit_group_remove_body, name),
            confirmText = stringResource(R.string.edit_group_remove_confirm),
            onConfirm = {
                confirmRemove = null
                onRemoveMember(memberId)
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { confirmRemove = null },
            danger = true,
        )
    }

    if (confirmLeave) {
        KitDialog(
            title = stringResource(R.string.group_leave_title),
            body = stringResource(R.string.group_leave_body),
            confirmText = stringResource(R.string.details_leave_confirm),
            onConfirm = onLeave,
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { confirmLeave = false },
            danger = true,
            loading = state.isLeaving,
            confirmEnabled = !state.isOffline,
        )
    }

    if (confirmDelete) {
        // Typing the name is the friction: this ends the group for everyone.
        var typed by rememberSaveable { mutableStateOf("") }
        KitDialog(
            title = stringResource(R.string.edit_group_delete_title),
            body = stringResource(R.string.edit_group_delete_body, group.name),
            confirmText = stringResource(R.string.edit_group_delete_confirm),
            onConfirm = onDelete,
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { confirmDelete = false },
            danger = true,
            loading = state.isDeleting,
            confirmEnabled = typed.trim() == group.name.trim() && !state.isOffline,
        ) {
            KitTextField(
                value = typed,
                onValueChange = { typed = it },
                label = stringResource(R.string.edit_group_delete_type_name),
            )
        }
    }
}

@Composable
private fun MembersCard(state: GroupDetailsUiState, onMemberOptions: (String) -> Unit) {
    val group = state.group ?: return
    val context = LocalContext.current
    val datePattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM") }
    KitCard(spacing = 2.dp) {
        CardHeader(
            title = stringResource(R.string.edit_group_members),
            detail = stringResource(R.string.details_members_count, state.members.size, group.maxMemberCap),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        state.members.forEach { member ->
            val isYou = member.userId == state.currentUserId
            val isAdminRow = member.userId == group.adminId
            PersonRow(
                name = if (isYou) stringResource(R.string.home_you) else member.displayName,
                avatarName = member.displayName,
                photoUrl = member.photoUrl,
                seed = member.userId,
                emphasised = isYou,
                detail = when {
                    isAdminRow -> stringResource(R.string.edit_group_member_admin)
                    member.joinedAt > 0L -> stringResource(
                        R.string.details_joined,
                        DateFormat.format(datePattern, Date(member.joinedAt)).toString(),
                    )
                    else -> null
                },
            ) {
                if (state.isAdmin && !isAdminRow) {
                    CircleIconButton(
                        icon = PamojaIcons.More,
                        contentDescription = stringResource(R.string.details_member_options_desc, member.displayName),
                        onClick = { onMemberOptions(member.userId) },
                        enabled = state.removingMemberId != member.userId,
                    )
                }
            }
        }
    }
}

/**
 * What Premium adds for everyone in the group. Locked rows still open their
 * screens, which explain what is inside; sponsoring opens the paywall.
 */
@Composable
private fun PremiumCard(
    access: GroupAccessState,
    shields: Int,
    salesEnabled: Boolean,
    onOpenReview: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val sponsor = shouldShowSponsorshipEntry(salesEnabled, access)
    val manage = access.isPremium || access.isPreview
    var explainShields by rememberSaveable { mutableStateOf(false) }
    if (explainShields) {
        KitDialog(
            title = stringResource(R.string.premium_shields),
            body = stringResource(R.string.premium_shields_explain),
            confirmText = stringResource(R.string.common_dismiss),
            onConfirm = { explainShields = false },
            onDismiss = { explainShields = false },
        )
    }
    KitCard(tone = CardTone.Hero, spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Text(
                stringResource(R.string.details_premium_title),
                style = Kit.type.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.weight(1f),
            )
            when (access) {
                is GroupAccessState.Premium -> PopChip(stringResource(R.string.details_premium_active))
                is GroupAccessState.Preview -> PopChip(stringResource(R.string.premium_preview_chip))
                else -> Unit
            }
        }
        // Shields: what they are when the group has them, the paywall when not.
        ListRow(
            stringResource(R.string.premium_shields),
            value = if (access.hasCircleAccess) {
                pluralStringResource(R.plurals.premium_shields_ready, shields, shields)
            } else {
                null
            },
            onClick = { if (access.hasCircleAccess) explainShields = true else onOpenPaywall() },
            leadingIcon = if (access.hasCircleAccess) null else PamojaIcons.Lock,
            showDivider = false,
        )
        if (sponsor) {
            ListRow(
                stringResource(R.string.group_sponsor_title),
                onClick = onOpenPaywall,
                leadingIcon = PamojaIcons.Star,
                showDivider = false,
            )
        } else if (manage) {
            ListRow(stringResource(R.string.details_premium_manage), onClick = onOpenPaywall, showDivider = false)
        }
    }
}

@Composable
private fun SettingSheet(
    which: DetailSheet,
    state: GroupDetailsUiState,
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveGoal: (Int) -> Unit,
    onSaveCap: (Int) -> Unit,
    onSaveWeekStart: (DayOfWeek) -> Unit,
    onSettingsSavedShown: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val group = state.group ?: return
    val title = stringResource(
        when (which) {
            DetailSheet.Name -> R.string.edit_group_name_label
            DetailSheet.Goal -> R.string.details_goal
            DetailSheet.Cap -> R.string.details_member_limit
            DetailSheet.WeekStart -> R.string.details_week_starts
            DetailSheet.Photo -> R.string.edit_group_photo
        },
    )
    KitSheet(onDismiss = onDismiss, title = title) { close ->
        // Saved: slide the sheet away. Failures stay open, with a snackbar.
        LaunchedEffect(state.settingsSaved) {
            if (state.settingsSaved) close(onSettingsSavedShown)
        }
        val canEdit = !state.isOffline && !state.isSaving
        when (which) {
            DetailSheet.Name -> {
                var name by rememberSaveable { mutableStateOf(group.name) }
                val max = UpdateGroupSettingsUseCase.MAX_GROUP_NAME_LENGTH
                val error = when {
                    name.isBlank() -> stringResource(R.string.create_group_name_required)
                    name.trim().length > max -> stringResource(R.string.create_group_name_too_long)
                    else -> null
                }
                KitTextField(
                    value = name,
                    onValueChange = { name = it.take(max) },
                    label = stringResource(R.string.edit_group_name_label),
                    helper = stringResource(R.string.group_name_character_count, name.length, max),
                    error = error,
                    enabled = !state.isSaving,
                )
                PrimaryButton(
                    stringResource(R.string.details_save),
                    onClick = { onSaveName(name.trim()) },
                    enabled = canEdit && error == null && name.trim() != group.name,
                    loading = state.isSaving,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            DetailSheet.Goal -> StepGoal.editOptionsFor(group.weeklyTarget).forEach { option ->
                OptionRow(
                    text = stringResource(R.string.details_goal_value, "%,d".format(option)),
                    selected = option == group.weeklyTarget,
                    enabled = canEdit,
                    onClick = { if (option == group.weeklyTarget) close {} else onSaveGoal(option) },
                )
            }

            DetailSheet.Cap -> {
                var cap by rememberSaveable { mutableIntStateOf(group.maxMemberCap) }
                val floor = state.minSelectableCap
                val ceiling = UpdateGroupSettingsUseCase.MAX_MEMBER_CAP
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    CircleIconButton(
                        PamojaIcons.Minus,
                        stringResource(R.string.details_fewer_desc),
                        onClick = { cap = (cap - 1).coerceAtLeast(floor) },
                        enabled = cap > floor,
                        container = Kit.colors.iconBackground,
                    )
                    Text(
                        cap.toString(),
                        style = Kit.type.number,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.size(width = 72.dp, height = 44.dp),
                    )
                    CircleIconButton(
                        PamojaIcons.Add,
                        stringResource(R.string.details_more_desc),
                        onClick = { cap = (cap + 1).coerceAtMost(ceiling) },
                        enabled = cap < ceiling,
                        container = Kit.colors.iconBackground,
                    )
                }
                // Says why the number stops, rather than leaving a dead button.
                if (cap == floor && floor > UpdateGroupSettingsUseCase.MIN_MEMBER_CAP) {
                    Text(
                        stringResource(R.string.edit_group_cap_floor, floor),
                        style = Kit.type.caption,
                        color = Kit.colors.muted,
                    )
                }
                PrimaryButton(
                    stringResource(R.string.details_save),
                    onClick = { onSaveCap(cap) },
                    enabled = canEdit && cap != group.maxMemberCap,
                    loading = state.isSaving,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            DetailSheet.WeekStart -> {
                if (state.weekStartLocked) {
                    Text(
                        stringResource(R.string.edit_group_week_start_planned, group.plannedWeekStart),
                        style = Kit.type.caption,
                        color = Kit.colors.muted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.edit_group_week_start_sub),
                        style = Kit.type.caption,
                        color = Kit.colors.muted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                    OptionRow(
                        text = day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        selected = day == group.startDay,
                        enabled = canEdit && !state.weekStartLocked,
                        onClick = { if (day == group.startDay) close {} else onSaveWeekStart(day) },
                    )
                }
            }

            DetailSheet.Photo -> {
                ActionRow(
                    stringResource(R.string.details_photo_choose),
                    icon = PamojaIcons.Camera,
                    enabled = !state.isOffline && !state.isUploadingPhoto,
                    onClick = { close(onChoosePhoto) },
                )
                if (group.photoUrl.isNotBlank()) {
                    ActionRow(
                        stringResource(R.string.details_photo_remove),
                        icon = PamojaIcons.Trash,
                        danger = true,
                        enabled = !state.isOffline && !state.isUploadingPhoto,
                        onClick = { close(onRemovePhoto) },
                    )
                }
            }
        }
    }
}

/**
 * Shows checkout only when both the release switch and server-backed access
 * state make it safe. An active Preview already includes the paid experience,
 * while Loading and Unavailable must fail closed rather than guessing.
 */
internal fun shouldShowSponsorshipEntry(
    subscriptionSalesEnabled: Boolean,
    groupAccess: GroupAccessState,
): Boolean = subscriptionSalesEnabled && when (groupAccess) {
    GroupAccessState.Free,
    is GroupAccessState.PreviewExpired -> true
    GroupAccessState.Loading,
    is GroupAccessState.Premium,
    is GroupAccessState.Preview,
    is GroupAccessState.Unavailable -> false
}
