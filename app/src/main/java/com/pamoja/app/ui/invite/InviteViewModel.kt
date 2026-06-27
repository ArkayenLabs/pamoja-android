package com.pamoja.app.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.analytics.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InviteUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val group: Group? = null
)

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val getGroupUseCase: GetGroupUseCase,
    private val analyticsManager: AnalyticsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteUiState())
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = InviteUiState(isLoading = true)
            val result = getGroupUseCase(groupId)
            result.fold(
                onSuccess = { group ->
                    _uiState.value = InviteUiState(group = group)
                    analyticsManager.logInviteScreenViewed(groupId)
                },
                onFailure = { error ->
                    _uiState.value = InviteUiState(
                        error = error.message ?: "Failed to load group"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}