package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.usecase.CreateUserUseCase
import com.pamoja.app.domain.usecase.SignUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val signUpUseCase: SignUpUseCase,
    private val createUserUseCase: CreateUserUseCase,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    fun signUpAndCreateProfile(
        email: String,
        password: String,
        name: String,
        age: Int?,
        height: Float?,
        weight: Float?
    ) {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState(isLoading = true)

            val authResult = signUpUseCase(email, password)
            authResult.fold(
                onSuccess = { authUser ->
                    val user = User(
                        userId = authUser.userId,
                        name = name,
                        age = age,
                        height = height,
                        weight = weight
                    )
                    val createResult = createUserUseCase(user)
                    createResult.fold(
                        onSuccess = {
                            userPreferences.saveUserId(authUser.userId)
                            userPreferences.saveUserName(name)
                            userPreferences.setOnboarded(true)
                            _user.value = user
                            _uiState.value = OnboardingUiState(isSuccess = true)
                        },
                        onFailure = { error ->
                            _uiState.value = OnboardingUiState(
                                error = error.message ?: "Failed to create profile"
                            )
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = OnboardingUiState(
                        error = error.message ?: "Sign up failed"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}