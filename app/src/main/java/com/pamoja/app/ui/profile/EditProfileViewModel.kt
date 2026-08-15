package com.pamoja.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.UnitConverter
import com.pamoja.app.domain.model.UnitSystem
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

    val name: String = "",
    val age: String = "",
    /** Centimetres in metric, whole feet in imperial. */
    val height: String = "",
    /** Inches, imperial only. Ignored in metric. */
    val heightInches: String = "",
    /** Kilograms in metric, pounds in imperial. */
    val weight: String = "",
    /**
     * Which units the fields above are being edited in.
     *
     * The fields hold display units, never storage units. Conversion happens on
     * load and on save, so a value written in pounds is still kilograms in
     * Firestore and does not change meaning when the setting does.
     */
    val unitSystem: UnitSystem = UnitSystem.Metric,
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
        observeUnits()
        load()
    }

    /**
     * Re-renders the fields when the unit setting changes.
     *
     * The canonical values are kept in [loaded], so a switch converts from
     * those rather than from whatever is currently typed. Converting the text
     * would round-trip it, and 180 cm to feet and back is 180.34 cm.
     */
    private fun observeUnits() {
        viewModelScope.launch {
            userPreferences.unitSystem.collect { system ->
                _uiState.value = _uiState.value.copy(unitSystem = system)
                loaded?.let { showMeasurements(it.height, it.weight, system) }
            }
        }
    }

    private fun showMeasurements(heightCm: Float?, weightKg: Float?, system: UnitSystem) {
        when (system) {
            UnitSystem.Metric -> _uiState.value = _uiState.value.copy(
                height = heightCm?.toInt()?.toString().orEmpty(),
                heightInches = "",
                weight = weightKg?.toInt()?.toString().orEmpty(),
            )

            UnitSystem.Imperial -> {
                val feetInches = heightCm?.let { UnitConverter.cmToFeetInches(it) }
                _uiState.value = _uiState.value.copy(
                    height = feetInches?.first?.toString().orEmpty(),
                    heightInches = feetInches?.second?.toString().orEmpty(),
                    weight = weightKg?.let { UnitConverter.kgToPounds(it).toString() }.orEmpty(),
                )
            }
        }
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
                        photoUrl = user.photoUrl,
                    )
                    showMeasurements(user.height, user.weight, _uiState.value.unitSystem)
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

    fun onHeightInchesChange(value: String) {
        _uiState.value = _uiState.value.copy(heightInches = value)
    }

    fun onWeightChange(value: String) {
        _uiState.value = _uiState.value.copy(weight = value)
    }

    fun save() {
        val base = loaded ?: return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, saveError = null)

            // Converted back to storage units here. Firestore always holds
            // centimetres and kilograms whatever the fields were showing.
            val heightCm: Float? = when (state.unitSystem) {
                UnitSystem.Metric -> state.height.toFloatOrNull()
                UnitSystem.Imperial -> {
                    val feet = state.height.toIntOrNull()
                    // Inches alone is not a height, but feet alone is: someone
                    // who types 5 and leaves inches blank means 5 feet 0.
                    feet?.let { UnitConverter.feetInchesToCm(it, state.heightInches.toIntOrNull() ?: 0) }
                }
            }

            val weightKg: Float? = when (state.unitSystem) {
                UnitSystem.Metric -> state.weight.toFloatOrNull()
                UnitSystem.Imperial -> state.weight.toIntOrNull()?.let { UnitConverter.poundsToKg(it) }
            }

            // copy, so photoUrl and deviceToken survive a write that does not
            // mention them.
            val updated = base.copy(
                name = state.name.trim(),
                age = state.age.toIntOrNull(),
                height = heightCm,
                weight = weightKg,
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
