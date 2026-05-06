package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.usecase.CreateUserUseCase
import com.pamoja.app.domain.usecase.SignInAnonymouslyUseCase
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
    private val signInAnonymouslyUseCase: SignInAnonymouslyUseCase,
    private val createUserUseCase: CreateUserUseCase,
    private val userPreferences: UserPreferences,
    private val firebaseAuth: FirebaseAuth          // injected to check existing session
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    fun signUpAndCreateProfile(
        name: String,
        age: Int?,
        height: Float?,
        weight: Float?
    ) {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState(isLoading = true)

            // ── Step 1: Get or create a Firebase anonymous user ──────────────────
            // If the Firebase SDK already holds a current user (e.g. user tapped Back
            // and re-submitted the form, or DataStore was wiped but Firebase token
            // is still valid) — reuse that UID instead of minting a new anonymous user.
            // Minting a new one would orphan all existing Firestore data under the old UID.
            val existingFirebaseUser = firebaseAuth.currentUser
            val userId: String

            if (existingFirebaseUser != null) {
                // Reuse the existing Firebase UID
                userId = existingFirebaseUser.uid
            } else {
                // First-ever launch — create an anonymous user
                val authResult = signInAnonymouslyUseCase()
                val authUser = authResult.getOrElse { error ->
                    _uiState.value = OnboardingUiState(
                        error = error.message ?: "Sign up failed. Please try again."
                    )
                    return@launch
                }
                userId = authUser.userId
            }

            // ── Step 2: Write the user profile to Firestore ──────────────────────
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
                },
                onFailure = { error ->
                    _uiState.value = OnboardingUiState(
                        error = error.message ?: "Failed to save profile. Check your connection."
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}