package com.pamoja.app.ui.weeklyreview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.ObserveGroupAccessUseCase
import com.pamoja.app.domain.usecase.ObserveGroupWeekSummariesUseCase
import com.pamoja.app.domain.usecase.ObserveLastFinishedWeekUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Current display data joined onto a historical user ID, when they are still here. */
data class WeeklyReviewMember(
    val displayName: String,
    val photoUrl: String?,
)

data class WeeklyReviewUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val access: GroupAccessState = GroupAccessState.Loading,
    /** The last finished week: free to every member. Null until one exists. */
    val lastWeek: GroupWeekSummary? = null,
    val lastWeekLoaded: Boolean = false,
    /** Every finished week, newest first. Premium (or its preview) only. */
    val history: List<GroupWeekSummary> = emptyList(),
    /** Which week the detail card shows; null means the last one. */
    val selectedWeekStart: String? = null,
    val members: Map<String, WeeklyReviewMember> = emptyMap(),
    val isOffline: Boolean = false,
    val loadError: AppError? = null,
    val isGroupUnavailable: Boolean = false,
) {
    val selectedWeek: GroupWeekSummary?
        get() = selectedWeekStart?.let { start -> history.firstOrNull { it.weekStart == start } } ?: lastWeek

    /** The rows under the detail card: every week except the last, which is already shown. */
    val earlierWeeks: List<GroupWeekSummary>
        get() = history.filter { it.weekStart != lastWeek?.weekStart }
}

/**
 * The week in review (REBUILD_PLAN.md Phase 6): the last finished week for
 * everyone, every earlier one with Premium.
 *
 * Four independent reads, each owning only its own part of the screen: the
 * group (and so which week was last), that week, access, and the full list
 * when access allows it. Names come from current memberships and are never
 * required: a historical row without a current member is shown without one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeeklyReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGroupUseCase: GetGroupUseCase,
    private val observeLastFinishedWeekUseCase: ObserveLastFinishedWeekUseCase,
    private val observeGroupWeekSummariesUseCase: ObserveGroupWeekSummariesUseCase,
    private val getGroupMembershipsUseCase: GetGroupMembershipsUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val observeGroupAccessUseCase: ObserveGroupAccessUseCase,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()
    private val _uiState = MutableStateFlow(WeeklyReviewUiState())
    val uiState: StateFlow<WeeklyReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online -> _uiState.update { it.copy(isOffline = !online) } }
        }
        load()
    }

    fun retry() = load()

    fun selectWeek(weekStart: String) {
        _uiState.update { it.copy(selectedWeekStart = weekStart.takeIf { start -> start != it.lastWeek?.weekStart }) }
    }

    /** Every listener of the current load, so Retry replaces them rather than adding more. */
    private var loadJob: Job? = null

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null, isGroupUnavailable = false) }
        loadJob = viewModelScope.launch { listen() }
    }

    private fun CoroutineScope.listen() {
        launch {
            getGroupUseCase.observe(groupId)
                .catch { error ->
                    val appError = error.toAppError()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = appError,
                            isGroupUnavailable = appError is AppError.NotFound || appError is AppError.PermissionDenied,
                        )
                    }
                }
                .collect { group -> _uiState.update { it.copy(isLoading = false, group = group) } }
        }

        // Follows the group's marker: a rollover while the screen is open
        // moves the free week along with it.
        launch {
            _uiState.map { it.group }
                .filterNotNull()
                .distinctUntilChanged { a, b -> a.streakThroughWeek == b.streakThroughWeek }
                .flatMapLatest { group -> observeLastFinishedWeekUseCase(group).catch { emit(null) } }
                .collect { week -> _uiState.update { it.copy(lastWeek = week, lastWeekLoaded = true) } }
        }

        launch {
            getGroupMembershipsUseCase(groupId)
                .catch { emit(emptyList()) }
                .collect { memberships -> _uiState.update { it.copy(members = memberships.toDisplayMap()) } }
        }

        launch {
            val user = getCurrentUserUseCase() ?: run {
                _uiState.update { it.copy(access = GroupAccessState.Unavailable(AppError.SessionExpired())) }
                return@launch
            }
            val access = observeGroupAccessUseCase(groupId, user.userId)
            launch { access.collect { state -> _uiState.update { it.copy(access = state) } } }
            access.map { it.hasCircleAccess }
                .distinctUntilChanged()
                .flatMapLatest { allowed ->
                    if (allowed) observeGroupWeekSummariesUseCase(groupId).catch { emit(emptyList()) }
                    else flowOf(emptyList())
                }
                .collect { weeks -> _uiState.update { it.copy(history = weeks) } }
        }
    }
}

private fun List<Membership>.toDisplayMap(): Map<String, WeeklyReviewMember> =
    mapNotNull { membership ->
        membership.displayName.trim().takeIf { it.isNotBlank() }?.let { name ->
            membership.userId to WeeklyReviewMember(
                displayName = name,
                photoUrl = membership.photoUrl.takeIf { it.isNotBlank() },
            )
        }
    }.toMap()
