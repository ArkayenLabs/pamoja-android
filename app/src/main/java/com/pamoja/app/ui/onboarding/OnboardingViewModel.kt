package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.usecase.CreateUserUseCase
import com.pamoja.app.domain.analytics.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val isSuccess: Boolean = false,
    val isOffline: Boolean = false,
) {
    /**
     * The profile write cannot be queued offline.
     *
     * Firestore only completes a write Task once the server acknowledges it, so
     * finishing onboarding without a network would suspend forever on a
     * spinner. Saying so leaves the user somewhere they can act.
     */
    val canSubmit: Boolean get() = !isLoading && !isOffline
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val createUserUseCase: CreateUserUseCase,
    private val userPreferences: UserPreferences,
    private val firebaseAuth: FirebaseAuth,          // injected to check existing session
    private val analyticsManager: AnalyticsManager,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private var profileSetupStartedLogged = false

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(
                    isOffline = !online,
                    error = if (online) null else _uiState.value.error,
                )
            }
        }
    }

    /** Call when ProfileSetupScreen enters composition. Logs once per session. */
    fun onProfileSetupStarted() {
        if (!profileSetupStartedLogged) {
            profileSetupStartedLogged = true
            analyticsManager.logProfileSetupStarted()
        }
    }

    fun createProfile(
        name: String,
        age: Int?,
        height: Float?,
        weight: Float?
    ) {
        viewModelScope.launch {
            // copy, not a fresh state, or isOffline resets to false on submit
            // and the guard un-hides the control it exists to block.
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Sign-in is a hard gate, so reaching this screen means Firebase
            // already holds a real account. There is nothing to create here, only
            // a profile to attach to the UID that already exists.
            val userId = firebaseAuth.currentUser?.uid
            if (userId == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = AppError.SessionExpired(),
                )
                return@launch
            }

            val user = User(
                userId = userId,
                name   = name,
                age    = age,
                height = height,
                weight = weight
            )
            val createResult = createUserUseCase(user)
            createResult.fold(
                onSuccess = {
                    userPreferences.saveUserId(userId)
                    userPreferences.saveUserName(name)
                    userPreferences.setOnboarded(true)
                    _user.value = user
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true,
                    )
                    analyticsManager.logProfileCompleted()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.toAppError(),
                    )
                }
            )
        }
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(isSuccess = false)
    }
}