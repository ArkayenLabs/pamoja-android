package com.pamoja.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetUserGroupsUseCase
import com.pamoja.app.domain.analytics.AnalyticsManager
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
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val analyticsManager: AnalyticsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var homeScreenReachedLogged = false

    init {
        loadHome()
    }

    private fun loadHome() {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = HomeUiState(isLoading = false, error = "Session expired. Please sign in.")
                return@launch
            }

            _uiState.value = _uiState.value.copy(userName = user.name, isLoading = false)

            if (!homeScreenReachedLogged) {
                homeScreenReachedLogged = true
                analyticsManager.logHomeScreenReached()
            }

            // Observe groups in real-time via the existing Firestore Flow
            getUserGroupsUseCase(user.userId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to load groups")
                }
                .collect { groups ->
                _uiState.value = _uiState.value.copy(groups = groups)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
