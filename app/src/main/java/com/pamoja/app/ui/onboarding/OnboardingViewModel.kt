package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
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
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val createUserUseCase: CreateUserUseCase,
    private val userPreferences: UserPreferences,
    private val firebaseAuth: FirebaseAuth,          // injected to check existing session
    private val analyticsManager: AnalyticsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private var profileSetupStartedLogged = false

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
            _uiState.value = OnboardingUiState(isLoading = true)

            // Sign-in is a hard gate, so reaching this screen means Firebase
            // already holds a real account. There is nothing to create here, only
            // a profile to attach to the UID that already exists.
            val userId = firebaseAuth.currentUser?.uid
            if (userId == null) {
                _uiState.value = OnboardingUiState(
                    error = "Your session expired. Please sign in again."
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
                    _uiState.value = OnboardingUiState(isSuccess = true)
                    analyticsManager.logProfileCompleted(userId)
                },
                onFailure = { error ->
                    _uiState.value = OnboardingUiState(
                        error = error.message ?: "Failed to save profile. Check your connection."
                    )
                }
            )
        }
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(isSuccess = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}