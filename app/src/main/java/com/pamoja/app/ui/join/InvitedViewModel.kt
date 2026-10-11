package com.pamoja.app.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.usecase.GetInviteSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The invite before sign-in (REBUILD_PLAN.md §6, step 2): what the group is,
 * then sign in to join it. Shares [JoinUiState] with the signed-in preview so
 * both draw the same card.
 *
 * Nothing here joins. "Join group" only remembers the decision with the
 * pending invite, and the signed-in preview acts on it once the account exists.
 */
@HiltViewModel
class InvitedViewModel @Inject constructor(
    private val getInviteSummary: GetInviteSummaryUseCase,
    private val userPreferences: UserPreferences,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinUiState())
    val uiState: StateFlow<JoinUiState> = _uiState.asStateFlow()

    private var code = ""

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    fun load(code: String) {
        this.code = code
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolving = true, resolveError = null)
            getInviteSummary(code).fold(
                onSuccess = { summary ->
                    _uiState.value = _uiState.value.copy(
                        isResolving = false,
                        group = Group(
                            groupId = summary.groupId,
                            name = summary.name,
                            memberCount = summary.memberCount,
                            maxMemberCap = summary.maxMemberCap,
                            weeklyTarget = summary.weeklyTarget,
                        ),
                        isFull = summary.isFull,
                        startedBy = summary.startedBy,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isResolving = false, resolveError = error.toAppError())
                },
            )
        }
    }

    fun retry() {
        if (code.isNotBlank()) load(code)
    }

    /** Join group, before an account exists: kept, and acted on after sign-in. */
    fun accept(onDone: () -> Unit) {
        viewModelScope.launch {
            userPreferences.savePendingInviteCode(code, accepted = true)
            // The tour would only stand between them and the group they chose.
            userPreferences.setIntroSeen(true)
            onDone()
        }
    }
}
