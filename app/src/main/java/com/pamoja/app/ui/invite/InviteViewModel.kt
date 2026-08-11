package com.pamoja.app.ui.invite

import androidx.lifecycle.ViewModel
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
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
    val error: AppError? = null,
    val group: Group? = null,
    val isOffline: Boolean = false,
    /**
     * True once the group has been asked for and answered, either way.
     *
     * Without it the screen cannot tell "still fetching" from "fetched, and the
     * cap really is 10", so it rendered the default cap as fact and then
     * corrected itself once the real one landed.
     */
    val hasLoadedOnce: Boolean = false,
)

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val getGroupUseCase: GetGroupUseCase,
    private val analyticsManager: AnalyticsManager,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteUiState())
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    private var loadedGroupId: String? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    fun loadGroup(groupId: String) {
        loadedGroupId = groupId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = getGroupUseCase(groupId)
            result.fold(
                onSuccess = { group ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        group = group,
                        error = null,
                    )
                    analyticsManager.logInviteScreenViewed(groupId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        error = error.toAppError(),
                    )
                }
            )
        }
    }

    /**
     * Only the group's details are retried.
     *
     * The invite link itself is derived from the group ID the screen was opened
     * with, never fetched, so sharing keeps working through all of this.
     */
    fun retry() {
        loadedGroupId?.let { loadGroup(it) }
    }
}