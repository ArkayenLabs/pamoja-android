package com.pamoja.app.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.JoinGroupUseCase
import com.pamoja.app.domain.usecase.ResolveInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinUiState(
    /** Resolving the code into a group. */
    val isResolving: Boolean = true,

    /** The join itself is in flight. Separate so only the button shows progress. */
    val isJoining: Boolean = false,

    val group: Group? = null,
    val isAlreadyMember: Boolean = false,
    val isFull: Boolean = false,

    /** Could not resolve the invite at all. */
    val resolveError: AppError? = null,

    /** Resolved fine, but the join failed. The preview stays on screen. */
    val joinError: AppError? = null,

    val isOffline: Boolean = false,

    /** Set on success, or immediately when they were already a member. */
    val joinedGroupId: String? = null,
) {
    /** Joining is only offered when it can actually succeed. */
    val canJoin: Boolean
        get() = group != null && !isAlreadyMember && !isFull && !isJoining && !isOffline
}

/**
 * Drives the invite preview.
 *
 * Replaces a flow where opening a link joined immediately and navigated, so the
 * first thing a person knew about a group was that they were in it. Now the
 * group is described first and joining is a decision.
 */
@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    private val resolveInviteUseCase: ResolveInviteUseCase,
    private val joinGroupUseCase: JoinGroupUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val analyticsManager: AnalyticsManager,
    private val userPreferences: UserPreferences,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinUiState())
    val uiState: StateFlow<JoinUiState> = _uiState.asStateFlow()

    private var code: String = ""

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    fun resolve(codeOrLink: String) {
        code = codeOrLink
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolving = true, resolveError = null)

            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = _uiState.value.copy(
                    isResolving = false,
                    resolveError = AppError.SessionExpired(),
                )
                return@launch
            }

            resolveInviteUseCase(codeOrLink, user.userId).fold(
                onSuccess = { preview ->
                    _uiState.value = _uiState.value.copy(
                        isResolving = false,
                        group = preview.group,
                        isAlreadyMember = preview.isAlreadyMember,
                        isFull = preview.isFull,
                    )
                    // The pending code has done its job. Cleared here so a dead
                    // invite cannot re-trigger this screen on every launch.
                    userPreferences.clearPendingInviteCode()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isResolving = false,
                        resolveError = error.toAppError(),
                    )
                    userPreferences.clearPendingInviteCode()
                }
            )
        }
    }

    fun join() {
        val group = _uiState.value.group ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isJoining = true, joinError = null)

            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = _uiState.value.copy(
                    isJoining = false,
                    joinError = AppError.SessionExpired(),
                )
                return@launch
            }

            joinGroupUseCase(code, user.userId).fold(
                onSuccess = {
                    analyticsManager.logInviteLinkUsed(group.groupId, user.userId)
                    analyticsManager.logGroupJoined(group.groupId, user.userId)
                    _uiState.value = _uiState.value.copy(
                        isJoining = false,
                        joinedGroupId = group.groupId,
                    )
                },
                onFailure = { error ->
                    val appError = error.toAppError()
                    _uiState.value = _uiState.value.copy(
                        isJoining = false,
                        joinError = appError,
                        // Someone took the last place between resolving and
                        // joining. Flip the preview to its full state so the
                        // button stops offering something that cannot work.
                        isFull = _uiState.value.isFull || appError is AppError.Conflict,
                    )
                }
            )
        }
    }

    /** For an existing member: straight through, no join attempted. */
    fun openExistingGroup() {
        val group = _uiState.value.group ?: return
        _uiState.value = _uiState.value.copy(joinedGroupId = group.groupId)
    }

    fun retry() {
        if (code.isNotBlank()) resolve(code)
    }

    fun clearNavigation() {
        _uiState.value = _uiState.value.copy(joinedGroupId = null)
    }
}
