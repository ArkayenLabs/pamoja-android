package com.pamoja.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.JoinGroupUseCase
import com.pamoja.app.domain.analytics.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateOrJoinUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val joinedGroupId: String? = null
)

@HiltViewModel
class CreateOrJoinViewModel @Inject constructor(
    private val joinGroupUseCase: JoinGroupUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val analyticsManager: AnalyticsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateOrJoinUiState())
    val uiState: StateFlow<CreateOrJoinUiState> = _uiState.asStateFlow()

    fun joinGroup(inviteLink: String) {
        viewModelScope.launch {
            _uiState.value = CreateOrJoinUiState(isLoading = true)

            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = CreateOrJoinUiState(error = "User not found. Please sign in again.")
                return@launch
            }

            val result = joinGroupUseCase(inviteLink, user.userId)
            result.fold(
                onSuccess = {
                    val groupId = inviteLink.removePrefix("pamoja://join/")
                    _uiState.value = CreateOrJoinUiState(joinedGroupId = groupId)
                    analyticsManager.logInviteLinkUsed(groupId, user.userId)
                    analyticsManager.logGroupJoined(groupId, user.userId)
                },
                onFailure = { error ->
                    _uiState.value = CreateOrJoinUiState(
                        error = error.message ?: "Failed to join group"
                    )
                }
            )
        }
    }

    fun clearJoinedGroupId() {
        _uiState.value = _uiState.value.copy(joinedGroupId = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}