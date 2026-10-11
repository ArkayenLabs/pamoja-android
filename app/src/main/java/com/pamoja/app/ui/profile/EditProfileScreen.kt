package com.pamoja.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.Avatar
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

/**
 * The profile editor that never existed.
 *
 * `ProfileSetupScreen` is onboarding-only and unreachable afterwards, so until
 * this screen a typo in your name was permanent. It edits the name and photo
 * only; age, height and weight are no longer collected anywhere.
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    /** Leaves the editor after a successful save. Separate from [onBack] so the
     *  destination can acknowledge the save; a plain back does not. */
    onSaved: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Picking no longer uploads directly. The picked image goes to the cropper
    // first, so the person choosing the photo decides which part of it becomes
    // the circular avatar rather than the centre-crop deciding for them.
    var pendingCropUri by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { pendingCropUri = it.toString() } }

    // Saving returns to where you came from: with the form still on screen and
    // unchanged, Save read as having done nothing in device testing. The
    // acknowledgement is shown on the destination instead.
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.clearSaved()
            onSaved()
        }
    }

    // Takes over the whole screen while framing, and returns here on either
    // outcome. Not a nav destination: the cropper is a step inside picking a
    // photo, and routing it would mean encoding a content:// URI into a route.
    pendingCropUri?.let { uri ->
        PhotoCropScreen(
            sourceUri = uri,
            onCancel = { pendingCropUri = null },
            onCropped = { croppedUri ->
                pendingCropUri = null
                viewModel.onPhotoPicked(croppedUri)
            },
        )
        return
    }

    EditProfileContent(
        state = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        onName = viewModel::onNameChange,
        onShowPhoto = viewModel::onShowPhotoInGroupsChange,
        onChoosePhoto = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onSave = viewModel::save,
    )
}

/** Stateless boundary for previews and tests. */
@Composable
internal fun EditProfileContent(
    state: EditProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onName: (String) -> Unit,
    onShowPhoto: (Boolean) -> Unit,
    onChoosePhoto: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val nameRequired = stringResource(R.string.profile_name_required)
    val nameTooLong = stringResource(R.string.profile_name_too_long)
    val nameError = when {
        state.name.isBlank() -> nameRequired
        state.name.trim().length > com.pamoja.app.domain.model.User.MAX_NAME_LENGTH -> nameTooLong
        else -> null
    }
    val ready = !state.isLoading && state.loadError == null

    KitScreen(
        gutter = 20.dp,
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
        bottom = if (ready) {
            {
                PrimaryButton(
                    text = stringResource(if (state.isSaving) R.string.profile_edit_saving else R.string.profile_edit_save),
                    onClick = onSave,
                    enabled = nameError == null && state.canSave,
                    loading = state.isSaving,
                )
            }
        } else {
            null
        },
    ) {
        Text(stringResource(R.string.profile_edit_title), style = Kit.type.headline)

        val loadError = state.loadError
        when {
            // Nothing loaded, so the failure is the screen.
            loadError != null -> {
                val copy = loadError.toErrorCopy()
                KitNotice(title = stringResource(copy.title), body = copy.body(context), danger = true)
                PrimaryButton(stringResource(R.string.common_try_again), onClick = onRetry)
            }

            state.isLoading -> {
                KitSkeleton(height = 120.dp)
                KitSkeleton(height = 90.dp)
            }

            else -> {
                Text(stringResource(R.string.profile_edit_subtitle), style = Kit.type.body, color = Kit.colors.muted)

                if (state.isOffline) {
                    KitNotice(
                        title = stringResource(R.string.profile_edit_offline_title),
                        body = stringResource(R.string.profile_edit_offline_body),
                    )
                }

                PhotoPickerAvatar(
                    name = state.name,
                    photoUrl = state.photoUrl,
                    isUploading = state.isUploadingPhoto,
                    enabled = !state.isUploadingPhoto && !state.isOffline,
                    onChoose = onChoosePhoto,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                KitTextField(
                    value = state.name,
                    onValueChange = onName,
                    label = stringResource(R.string.profile_name_label),
                    placeholder = stringResource(R.string.profile_name_placeholder),
                    enabled = !state.isSaving,
                    error = nameError,
                )

                // Sits right under the photo's own controls, because it is a
                // fact about that photo, and only once there is a photo: asking
                // someone with no avatar whether to share it is a question about
                // nothing.
                if (!state.photoUrl.isNullOrBlank()) {
                    ShowPhotoInGroupsRow(
                        checked = state.showPhotoInGroups,
                        onCheckedChange = onShowPhoto,
                        // Offline it would appear to work and then silently
                        // revert, which for a privacy control is the wrong kind
                        // of surprise.
                        enabled = !state.isUpdatingPhotoPrivacy && !state.isOffline,
                    )
                }

                state.saveError?.takeIf { !state.isOffline }?.let {
                    KitNotice(
                        title = stringResource(R.string.profile_edit_failed_title),
                        body = it.toErrorCopy().body(context),
                        danger = true,
                    )
                }
            }
        }
    }
}

/**
 * Your photo with a camera button on its corner, which opens the picker. Shared
 * by Edit profile and the sign-up name step. Still the photo picker, not the
 * camera permission: Android's picker runs outside the app and returns one
 * image, so there is no READ_MEDIA_IMAGES to request and no permanently-denied
 * state.
 */
@Composable
internal fun PhotoPickerAvatar(
    name: String,
    photoUrl: String?,
    isUploading: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(name = name, photoUrl = photoUrl, size = 112.dp)
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Kit.colors.ink,
                    strokeWidth = 2.5.dp,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 6.dp, y = 6.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Kit.colors.page)
                .padding(3.dp)
                .clip(CircleShape)
                .background(Kit.colors.button)
                .clickable(enabled = enabled, role = Role.Button, onClick = onChoose),
        ) {
            Icon(
                painter = painterResource(PamojaIcons.Camera),
                // The spinner is the only visual sign of an upload and says
                // nothing to a screen reader; this does.
                contentDescription = stringResource(
                    when {
                        isUploading -> R.string.profile_edit_photo_uploading
                        photoUrl.isNullOrBlank() -> R.string.profile_edit_photo_add
                        else -> R.string.profile_edit_photo_change
                    },
                ),
                tint = Kit.colors.onButton,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Whether your photo is shown in your groups. Shown only once there is a
 * photo: asking someone with no avatar whether to share it is a question about
 * nothing. Off by default (User.showPhotoInGroups).
 */
@Composable
internal fun ShowPhotoInGroupsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    KitCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.profile_show_photo_title), style = Kit.type.bodyStrong)
                Text(stringResource(R.string.profile_show_photo_sub), style = Kit.type.caption, color = Kit.colors.muted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Kit.colors.onButton,
                    checkedTrackColor = Kit.colors.button,
                    checkedBorderColor = Kit.colors.button,
                    uncheckedThumbColor = Kit.colors.muted,
                    uncheckedTrackColor = Kit.colors.track,
                    uncheckedBorderColor = Kit.colors.fieldBorder,
                ),
            )
        }
    }
}
