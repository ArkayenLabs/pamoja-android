package com.pamoja.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetUserGroupsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val groups: List<Group> = emptyList(),
    val userName: String = "",
    /**
     * Typed rather than a String so the screen can decide between a full error
     * state and a snackbar, and whether Retry is worth offering.
     */
    val error: AppError? = null,
    val isOffline: Boolean = false,
    /**
     * True once groups have arrived at least once. Distinguishes "still
     * loading" from "genuinely has no groups", which otherwise both look like
     * an empty list and produce an empty state that flashes before content.
     */
    val hasLoadedOnce: Boolean = false,
) {
    /** Content is worth showing even mid-error if we already have some. */
    val hasContent: Boolean get() = groups.isNotEmpty()

    val showEmptyState: Boolean get() = hasLoadedOnce && groups.isEmpty() && error == null

    /** A full-screen error is only right when there is nothing else to show. */
    val showErrorState: Boolean get() = error != null && !hasContent

    /** Otherwise the error is a passing note over content that still stands. */
    val showErrorSnackbar: Boolean get() = error != null && hasContent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val analyticsManager: AnalyticsManager,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var homeScreenReachedLogged = false

    init {
        observeConnectivity()
        loadHome()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    private fun loadHome() {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = AppError.SessionExpired(),
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(userName = user.name, isLoading = false)

            if (!homeScreenReachedLogged) {
                homeScreenReachedLogged = true
                analyticsManager.logHomeScreenReached()
            }

            getUserGroupsUseCase(user.userId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        error = e.toAppError(),
                    )
                }
                .collect { groups ->
                    _uiState.value = _uiState.value.copy(
                        groups = groups,
                        hasLoadedOnce = true,
                        // A successful emission clears whatever failed before it.
                        error = null,
                    )
                }
        }
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        loadHome()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
