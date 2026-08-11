package com.pamoja.app.ui.group

import androidx.lifecycle.ViewModel
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import androidx.lifecycle.viewModelScope
import com.pamoja.app.domain.usecase.CreateGroupUseCase
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.analytics.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val createdGroupId: String? = null
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val createGroupUseCase: CreateGroupUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val analyticsManager: AnalyticsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    fun createGroup(
        name: String,
        weeklyTarget: Int,
        maxMemberCap: Int,
        canMembersEditTarget: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = CreateGroupUiState(isLoading = true)

            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = CreateGroupUiState(
                    error = AppError.SessionExpired()
                )
                return@launch
            }

            val result = createGroupUseCase(
                name = name,
                adminId = user.userId,
                weeklyTarget = weeklyTarget,
                maxMemberCap = maxMemberCap,
                canMembersEditTarget = canMembersEditTarget
            )

            result.fold(
                onSuccess = { group ->
                    _uiState.value = CreateGroupUiState(createdGroupId = group.groupId)
                    analyticsManager.logGroupCreated(group.groupId, name)
                },
                onFailure = { error ->
                    _uiState.value = CreateGroupUiState(
                        error = error.toAppError()
                    )
                }
            )
        }
    }

    fun clearCreatedGroupId() {
        _uiState.value = _uiState.value.copy(createdGroupId = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}