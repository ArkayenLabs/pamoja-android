package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pamoja.app.data.local.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SignInUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun signIn(email: String, password: String) {
        if (email.isBlank()) {
            _uiState.value = SignInUiState(error = "Please enter your email")
            return
        }
        if (password.isBlank()) {
            _uiState.value = SignInUiState(error = "Please enter your password")
            return
        }

        viewModelScope.launch {
            _uiState.value = SignInUiState(isLoading = true)
            try {
                val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val user = result.user
                    ?: throw Exception("Sign in failed. Please try again.")

                // Restore local session so the app knows who is logged in
                userPreferences.saveUserId(user.uid)
                userPreferences.saveUserName(user.displayName ?: "")
                userPreferences.setOnboarded(true)

                _uiState.value = SignInUiState(isSuccess = true)
            } catch (e: Exception) {
                val message = when {
                    e.message?.contains("password") == true -> "Incorrect password. Please try again."
                    e.message?.contains("no user") == true -> "No account found with this email."
                    e.message?.contains("email") == true -> "Please enter a valid email address."
                    else -> e.message ?: "Sign in failed. Please try again."
                }
                _uiState.value = SignInUiState(error = message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
