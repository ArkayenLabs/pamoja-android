package com.pamoja.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.CheckRow
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.profile.PhotoCropScreen
import com.pamoja.app.ui.profile.PhotoPickerAvatar
import com.pamoja.app.ui.profile.ShowPhotoInGroupsRow

/**
 * The name step every new account passes through (REBUILD_PLAN.md §6).
 *
 * Opens prefilled with the name Google or the email sign-up form provided;
 * phone accounts start empty. It is also where the Terms' 18+ requirement is
 * asked, which is why no new account skips it any more.
 *
 * [onContinue] is told whether an invite is waiting: with one, Home opens its
 * preview; without, the next step is starting a group.
 */
@Composable
fun ProfileSetupScreen(
    onContinue: (hasPendingInvite: Boolean) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var isAdult by rememberSaveable { mutableStateOf(false) }
    // Optional, as decided for the rebuild: a name, and a photo if you like.
    // Held as the cropped local file until the profile exists to attach it to.
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showPhoto by rememberSaveable { mutableStateOf(false) }
    var pendingCropUri by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { pendingCropUri = it.toString() } }

    val nameRequired = stringResource(R.string.profile_name_required)
    val nameTooLong = stringResource(R.string.profile_name_too_long)
    val nameError = when {
        name.isBlank() -> nameRequired
        name.trim().length > com.pamoja.app.domain.model.User.MAX_NAME_LENGTH -> nameTooLong
        else -> null
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.clearSuccess()
            onContinue(uiState.hasPendingInvite)
        }
    }
    LaunchedEffect(uiState.suggestedName) {
        if (name.isBlank() && uiState.suggestedName.isNotBlank()) {
            name = uiState.suggestedName
        }
    }
    LaunchedEffect(Unit) { viewModel.onProfileSetupStarted() }

    pendingCropUri?.let { uri ->
        PhotoCropScreen(
            sourceUri = uri,
            onCancel = { pendingCropUri = null },
            onCropped = { cropped ->
                pendingCropUri = null
                photoUri = cropped
            },
        )
        return
    }

    ProfileSetupContent(
        name = name,
        onNameChange = { name = it },
        nameError = nameError,
        isAdult = isAdult,
        onAdultChange = { isAdult = it },
        isLoading = uiState.isLoading,
        isOffline = uiState.isOffline,
        errorBody = uiState.error?.toErrorCopy()?.body(context),
        onSubmit = {
            viewModel.createProfile(name = name.trim(), photoUri = photoUri, showPhotoInGroups = showPhoto)
        },
        photoUri = photoUri,
        onChoosePhoto = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        showPhoto = showPhoto,
        onShowPhotoChange = { showPhoto = it },
    )
}

/** Stateless rendering boundary used by visual and interaction tests. */
@Composable
internal fun ProfileSetupContent(
    name: String,
    onNameChange: (String) -> Unit,
    nameError: String?,
    isAdult: Boolean,
    onAdultChange: (Boolean) -> Unit,
    isLoading: Boolean,
    isOffline: Boolean,
    errorBody: String?,
    onSubmit: () -> Unit,
    photoUri: String? = null,
    /** Null hides the photo (tests and previews that only exercise the name). */
    onChoosePhoto: (() -> Unit)? = null,
    showPhoto: Boolean = false,
    onShowPhotoChange: (Boolean) -> Unit = {},
) {
    var touched by rememberSaveable { mutableStateOf(false) }
    KitScreen(
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.common_continue),
                onClick = onSubmit,
                enabled = nameError == null && isAdult && !isOffline,
                loading = isLoading,
            )
        },
    ) {
        Text(stringResource(R.string.name_step_title), style = Kit.type.headline)
        Text(stringResource(R.string.name_step_body), style = Kit.type.body, color = Kit.colors.muted)
        onChoosePhoto?.let { choose ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PhotoPickerAvatar(
                    name = name,
                    photoUrl = photoUri,
                    isUploading = false,
                    enabled = !isLoading,
                    onChoose = choose,
                )
            }
        }
        KitTextField(
            value = name,
            onValueChange = {
                touched = true
                onNameChange(it)
            },
            label = stringResource(R.string.profile_name_label),
            placeholder = stringResource(R.string.profile_name_placeholder),
            // An empty untouched field is not an error yet, but a prefilled
            // name that is too long is: otherwise Continue is simply grey.
            error = nameError.takeIf { touched || name.isNotBlank() },
            enabled = !isLoading,
        )
        if (photoUri != null) {
            ShowPhotoInGroupsRow(checked = showPhoto, onCheckedChange = onShowPhotoChange, enabled = !isLoading)
        }
        CheckRow(
            checked = isAdult,
            onCheckedChange = onAdultChange,
            text = stringResource(R.string.name_step_adult),
            enabled = !isLoading,
        )
        if (isOffline) {
            KitNotice(
                title = stringResource(R.string.profile_offline_title),
                body = stringResource(R.string.profile_offline_body),
            )
        } else if (errorBody != null) {
            KitNotice(title = stringResource(R.string.profile_failed_title), body = errorBody, danger = true)
        }
    }
}
