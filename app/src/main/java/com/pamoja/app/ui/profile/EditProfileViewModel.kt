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

    val name: String = "",
    val age: String = "",
    val height: String = "",
    val weight: String = "",
) {
    /**
     * Saving is a Firestore write, and a write Task only completes on server
     * acknowledgement. Offline it would suspend forever behind a spinner.
     */
    val canSave: Boolean get() = !isSaving && !isOffline && loadError == null
}

/**
 * Edits the profile captured during onboarding.
 *
 * Until now those four fields were write-once: `ProfileSetupScreen` is
 * onboarding-only and unreachable afterwards, so age, height and weight were
 * collected and could never be corrected or removed. That is a
 * data-minimisation problem as much as a usability one.
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val updateUserUseCase: UpdateUserUseCase,
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
                        // Rendered blank rather than "null", and blank on the way
                        // back out means the user cleared it deliberately.
                        age = user.age?.toString().orEmpty(),
                        height = user.height?.toInt()?.toString().orEmpty(),
                        weight = user.weight?.toInt()?.toString().orEmpty(),
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

    fun onAgeChange(value: String) {
        _uiState.value = _uiState.value.copy(age = value)
    }

    fun onHeightChange(value: String) {
        _uiState.value = _uiState.value.copy(height = value)
    }

    fun onWeightChange(value: String) {
        _uiState.value = _uiState.value.copy(weight = value)
    }

    fun save() {
        val base = loaded ?: return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, saveError = null)

            // copy, so photoUrl and deviceToken survive a write that does not
            // mention them.
            val updated = base.copy(
                name = state.name.trim(),
                age = state.age.toIntOrNull(),
                height = state.height.toFloatOrNull(),
                weight = state.weight.toFloatOrNull(),
            )

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

    fun clearSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }
}
