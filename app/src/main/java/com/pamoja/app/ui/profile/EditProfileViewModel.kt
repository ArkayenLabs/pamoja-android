package com.pamoja.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetUserUseCase
import com.pamoja.app.domain.usecase.UpdateAvatarUseCase
import com.pamoja.app.domain.usecase.UpdateUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** The failure that stopped the profile loading, which is a whole-screen state. */
    val loadError: AppError? = null,
    /** The failure that stopped a save, which sits next to the button instead. */
    val saveError: AppError? = null,
    val isOffline: Boolean = false,
    val isSaved: Boolean = false,

    /**
     * Whether this person's photo is shown to other members of their groups.
     *
     * Defaults false, matching the stored default. A user whose profile has not
     * loaded yet must never be shown as sharing.
     */
    val showPhotoInGroups: Boolean = false,
    /** The privacy switch writes immediately, so it has its own busy flag. */
    val isUpdatingPhotoPrivacy: Boolean = false,

    val name: String = "",
    /** Current photo URL, null when the user has never set one. */
    val photoUrl: String? = null,
    val isUploadingPhoto: Boolean = false,
) {
    /**
     * Saving is a Firestore write, and a write Task only completes on server
     * acknowledgement. Offline it would suspend forever behind a spinner.
     */
    val canSave: Boolean get() = !isSaving && !isOffline && loadError == null
}

/**
 * Edits the name and photo.
 *
 * Age, height and weight used to be edited here too. Nothing in the app used
 * them, so they are no longer shown, and a save leaves any stored values
 * exactly as they were rather than round-tripping them through unit
 * conversion. Clearing them is a later, deliberate migration.
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val updateUserUseCase: UpdateUserUseCase,
    private val updateAvatarUseCase: UpdateAvatarUseCase,
    private val userPreferences: UserPreferences,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    /**
     * The document as it was loaded.
     *
     * Kept whole so a save can copy onto it. `updateUser` writes the entire
     * document with `set()`, so building a fresh User from just the edited
     * fields silently erased everything not on this screen. Renaming from
     * Settings used to do exactly that to age, height and weight.
     */
    private var loaded: User? = null

    init {
        observeConnectivity()
        load()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(
                    isOffline = !online,
                    saveError = if (online) null else _uiState.value.saveError,
                )
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)

            val current = getCurrentUserUseCase()
            if (current == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = AppError.SessionExpired(),
                )
                return@launch
            }

            getUserUseCase(current.userId).fold(
                onSuccess = { user ->
                    loaded = user
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        name = user.name,
                        photoUrl = user.photoUrl,
                        showPhotoInGroups = user.showPhotoInGroups,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadError = e.toAppError(),
                    )
                }
            )
        }
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    /**
     * Applies immediately, unlike every other field on this screen.
     *
     * Deliberate. This is a privacy control, and the failure mode of deferring
     * it to Save is that someone switches their photo off, leaves without
     * pressing Save, and it stays visible to their groups. Nobody expects that
     * from a switch.
     *
     * The switch moves first so it feels instant, and moves back if the write
     * fails, because a control showing "off" while the photo is still shared
     * would be worse than one that visibly refused.
     */
    fun onShowPhotoInGroupsChange(enabled: Boolean) {
        val base = loaded ?: return
        val previous = _uiState.value.showPhotoInGroups
        _uiState.value = _uiState.value.copy(
            showPhotoInGroups = enabled,
            isUpdatingPhotoPrivacy = true,
            saveError = null,
        )

        viewModelScope.launch {
            val updated = base.copy(showPhotoInGroups = enabled)
            updateUserUseCase(updated).fold(
                onSuccess = {
                    loaded = updated
                    _uiState.value = _uiState.value.copy(isUpdatingPhotoPrivacy = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        showPhotoInGroups = previous,
                        isUpdatingPhotoPrivacy = false,
                        saveError = e.toAppError(),
                    )
                }
            )
        }
    }

    fun save() {
        val base = loaded ?: return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, saveError = null)

            // copy, so photoUrl, the photo-sharing choice and any stored age,
            // height and weight survive a write that does not mention them.
            val updated = base.copy(name = state.name.trim())

            updateUserUseCase(updated).fold(
                onSuccess = {
                    loaded = updated
                    userPreferences.saveUserName(updated.name)
                    _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveError = e.toAppError(),
                    )
                }
            )
        }
    }

    /**
     * Uploads a newly picked photo straight away, without waiting for Save.
     *
     * Picking a picture reads as a completed action, so making it depend on a
     * button further down the form is how someone leaves believing they changed
     * their photo when they did not. It is its own write, separate from the
     * rest of the form.
     */
    fun onPhotoPicked(imageUri: String) {
        val base = loaded ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true, saveError = null)

            updateAvatarUseCase(base, imageUri).fold(
                onSuccess = { url ->
                    loaded = base.copy(photoUrl = url)
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        photoUrl = url,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        saveError = e.toAppError(),
                    )
                }
            )
        }
    }

    fun clearSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }
}
