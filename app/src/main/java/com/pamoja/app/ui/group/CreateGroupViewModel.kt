package com.pamoja.app.ui.group

import androidx.lifecycle.ViewModel
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import androidx.lifecycle.viewModelScope
import com.pamoja.app.domain.usecase.CreateGroupUseCase
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.util.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val createdGroupId: String? = null,
    val isOffline: Boolean = false,
) {
    /**
     * Creating a group cannot be queued.
     *
     * Firestore's write Task only completes once the server acknowledges it, so
     * calling this offline suspends forever and the button spins with nothing
     * behind it. Better to say so up front than to fake progress.
     */
    val canSubmit: Boolean get() = !isLoading && !isOffline
}

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val createGroupUseCase: CreateGroupUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val analyticsManager: AnalyticsManager,
    private val connectivityObserver: ConnectivityObserver,
    private val workManagerScheduler: WorkManagerScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(
                    isOffline = !online,
                    // A failure caused by having no network has nothing left to
                    // say once the network is back.
                    error = if (online) null else _uiState.value.error,
                )
            }
        }
    }

    fun createGroup(
        name: String,
        dailyPerPersonTarget: Int,
        maxMemberCap: Int,
        canMembersEditTarget: Boolean,
        weekStartDay: DayOfWeek,
    ) {
        viewModelScope.launch {
            // copy, not a fresh state: rebuilding it dropped isOffline back to
            // false on every submit, which un-hid the very control being guarded.
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = AppError.SessionExpired(),
                )
                return@launch
            }

            val result = createGroupUseCase(
                name = name,
                adminId = user.userId,
                dailyPerPersonTarget = dailyPerPersonTarget,
                maxMemberCap = maxMemberCap,
                canMembersEditTarget = canMembersEditTarget,
                weekStartDay = weekStartDay,
            )

            result.fold(
                onSuccess = { group ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        createdGroupId = group.groupId,
                    )
                    analyticsManager.logGroupCreated(group.groupId, name)

                    // A group is created with no cached weekly total, and Home
                    // draws no progress at all until one exists. Without this
                    // the group a user just made is the one that looks broken,
                    // until an unrelated sync happens to fill it in.
                    workManagerScheduler.syncSoon()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.toAppError(),
                    )
                }
            )
        }
    }

    fun clearCreatedGroupId() {
        _uiState.value = _uiState.value.copy(createdGroupId = null)
    }
}