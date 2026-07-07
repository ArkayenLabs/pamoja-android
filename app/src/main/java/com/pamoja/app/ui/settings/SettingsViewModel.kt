package com.pamoja.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.usecase.DeleteAccountUseCase
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetUserUseCase
import com.pamoja.app.domain.usecase.SignOutUseCase
import com.pamoja.app.domain.usecase.UpdateUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val userName: String = "",
    val userId: String = "",
    val isSignedOut: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val updateUserUseCase: UpdateUserUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserDetails()
    }

    private fun loadUserDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Get local cached values first
            val localUserId = userPreferences.userId.firstOrNull() ?: ""
            val localName = userPreferences.userName.firstOrNull() ?: ""
            
            _uiState.value = _uiState.value.copy(
                userId = localUserId,
                userName = localName
            )

            // Verify with remote source if available
            val currentUser = getCurrentUserUseCase()
            if (currentUser != null) {
                val remoteUserResult = getUserUseCase(currentUser.userId)
                remoteUserResult.fold(
                    onSuccess = { user ->
                        // Cache remote name locally if it changed
                        if (user.name != localName) {
                            userPreferences.saveUserName(user.name)
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            userId = user.userId,
                            userName = user.name
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateUserName(newName: String) {
        if (newName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Name cannot be empty")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            val currentUserId = _uiState.value.userId
            
            val user = User(userId = currentUserId, name = newName)
            val result = updateUserUseCase(user)
            
            result.fold(
                onSuccess = {
                    userPreferences.saveUserName(newName)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userName = newName,
                        successMessage = "Name updated successfully"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to update name"
                    )
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = signOutUseCase()
            result.fold(
                onSuccess = {
                    userPreferences.clearAll()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedOut = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Sign out failed"
                    )
                }
            )
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = deleteAccountUseCase()
            result.fold(
                onSuccess = {
                    userPreferences.clearAll()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedOut = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to delete account"
                    )
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
