package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.SignInAnonymouslyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

/**
 * Handles the "returning user" sign-in flow.
 *
 * Because this app uses anonymous Firebase auth, there is no email/password to validate.
 * The correct "returning user" flow is:
 *
 *   1. Check if Firebase already has a current user → if yes, just restore session and go Home.
 *   2. If Firebase has NO current user (fresh install / uninstall-reinstall) → create a new
 *      anonymous user.  Their old data is gone (unavoidable with anonymous auth on a new install)
 *      but they start fresh cleanly.
 *
 * The name field is kept so returning users who remember their display name can provide it
 * for the DataStore cache. It is NOT used for authentication.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signInAnonymouslyUseCase: SignInAnonymouslyUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /**
     * Restores or creates an anonymous session.
     * For anonymous auth, we don't need any credentials.
     */
    fun signIn() {
        viewModelScope.launch {
            _uiState.value = SignInUiState(isLoading = true)
            try {
                // Check if Firebase already has an active anonymous session
                val existingUser = getCurrentUserUseCase()
                if (existingUser != null) {
                    // Session already exists — just update DataStore and continue
                    userPreferences.saveUserId(existingUser.userId)
                    userPreferences.setOnboarded(true)
                    _uiState.value = SignInUiState(isSuccess = true)
                    return@launch
                }

                // No existing session — create a new anonymous user
                val result = signInAnonymouslyUseCase()
                result.fold(
                    onSuccess = { user ->
                        userPreferences.saveUserId(user.userId)
                        userPreferences.setOnboarded(true)
                        _uiState.value = SignInUiState(isSuccess = true)
                    },
                    onFailure = { error ->
                        _uiState.value = SignInUiState(
                            error = error.message ?: "Something went wrong. Please try again."
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = SignInUiState(
                    error = e.message ?: "Something went wrong. Please try again."
                )
            }
        }
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(isSuccess = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
