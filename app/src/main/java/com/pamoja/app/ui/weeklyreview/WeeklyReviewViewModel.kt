package com.pamoja.app.ui.weeklyreview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.di.CirclePreviewEnabled
import com.pamoja.app.di.DayOnePlanningEnabled
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.NextWeekPlan
import com.pamoja.app.domain.model.NextWeekPlanChoice
import com.pamoja.app.domain.model.NextWeekPlanResponse
import com.pamoja.app.domain.model.NextWeekResponse
import com.pamoja.app.domain.model.NextWeekResponseCounts
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.ObserveGroupAccessUseCase
import com.pamoja.app.domain.usecase.ObserveGroupWeekSummariesUseCase
import com.pamoja.app.domain.usecase.ObserveNextWeekPlanUseCase
import com.pamoja.app.domain.usecase.ObserveMyNextWeekResponseUseCase
import com.pamoja.app.domain.usecase.SaveNextWeekPlanUseCase
import com.pamoja.app.domain.usecase.SaveNextWeekResponseUseCase
import com.pamoja.app.domain.usecase.upcomingWeekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Current profile information that may be joined onto a historical user ID. */
data class WeeklyReviewMember(
    val displayName: String,
    val photoUrl: String?,
)

data class WeeklyReviewUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val weeks: List<GroupWeekSummary> = emptyList(),
    val currentMembers: Map<String, WeeklyReviewMember> = emptyMap(),
    val historyLoaded: Boolean = false,
    val isOffline: Boolean = false,
    val loadError: AppError? = null,
    val isGroupUnavailable: Boolean = false,
    val circlePreviewEnabled: Boolean = false,
    val groupAccess: GroupAccessState = GroupAccessState.Loading,
    val currentUserId: String = "",
    val nextWeekStart: String = "",
    val nextWeekPlan: NextWeekPlan? = null,
    val myNextWeekPlanResponse: NextWeekPlanResponse? = null,
    val nextWeekPlanLoaded: Boolean = false,
    val isSavingNextWeekPlan: Boolean = false,
    val isSavingNextWeekResponse: Boolean = false,
    val nextWeekPlanError: AppError? = null,
    val dayOnePlanningEnabled: Boolean = false,
    val planningTimeZone: String = "",
    val scheduledGoal: com.pamoja.app.domain.model.ScheduledGoal? = null,
) {
    val canReadCircleHistory: Boolean
        get() = !accessGated || groupAccess.hasCircleAccess

    val accessGated: Boolean get() = circlePreviewEnabled || dayOnePlanningEnabled

    val isEmpty: Boolean
        get() = canReadCircleHistory && historyLoaded && weeks.isEmpty() && loadError == null

    val hitCount: Int
        get() = weeks.count { it.goalHit }

    val missedCount: Int
        get() = weeks.size - hitCount

    val bestWeek: GroupWeekSummary?
        get() = weeks.maxByOrNull { it.totalSteps }

    val isOrganizer: Boolean
        get() = currentUserId.isNotBlank() && group?.adminId == currentUserId

    val myNextWeekResponse: NextWeekResponse?
        get() = myNextWeekPlanResponse?.takeIf { it.userId == currentUserId }?.response

    val nextWeekResponseCounts: NextWeekResponseCounts
        get() = nextWeekPlan?.responseCounts ?: NextWeekResponseCounts()
}

/**
 * Owns the Weekly Review's group, access, history and display-name reads.
 *
 * History is the screen's core data. The membership stream is optional display
 * enrichment only: when a current member can still be resolved, their name and
 * opted-in photo can sit beside their historical contribution. A user ID is
 * never shown as a fallback.
 */
@HiltViewModel
class WeeklyReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGroupUseCase: GetGroupUseCase,
    private val observeGroupWeekSummariesUseCase: ObserveGroupWeekSummariesUseCase,
    private val getGroupMembershipsUseCase: GetGroupMembershipsUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val observeGroupAccessUseCase: ObserveGroupAccessUseCase,
    private val observeNextWeekPlanUseCase: ObserveNextWeekPlanUseCase,
    private val observeMyNextWeekResponseUseCase: ObserveMyNextWeekResponseUseCase,
    private val saveNextWeekPlanUseCase: SaveNextWeekPlanUseCase,
    private val saveNextWeekResponseUseCase: SaveNextWeekResponseUseCase,
    connectivityObserver: ConnectivityObserver,
    private val clock: Clock,
    @CirclePreviewEnabled private val circlePreviewEnabled: Boolean,
    @DayOnePlanningEnabled private val dayOnePlanningEnabled: Boolean = false,
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()
    private val _uiState = MutableStateFlow(
        WeeklyReviewUiState(
            circlePreviewEnabled = circlePreviewEnabled,
            dayOnePlanningEnabled = dayOnePlanningEnabled,
        ),
    )
    val uiState: StateFlow<WeeklyReviewUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOffline = !online) }
            }
        }
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadError = null,
                    isGroupUnavailable = false,
                    weeks = if (circlePreviewEnabled) emptyList() else it.weeks,
                    historyLoaded = if (circlePreviewEnabled) false else it.historyLoaded,
                    groupAccess = GroupAccessState.Loading,
                    nextWeekPlan = null,
                    myNextWeekPlanResponse = null,
                    nextWeekPlanLoaded = false,
                    nextWeekPlanError = null,
                    scheduledGoal = null,
                )
            }

            val group = getGroupUseCase(groupId).getOrElse { throwable ->
                val error = throwable.toAppError()
                val unavailable = error.isGroupGone()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = error,
                        isGroupUnavailable = unavailable,
                        group = if (unavailable) null else it.group,
                        weeks = if (unavailable) emptyList() else it.weeks,
                        currentMembers = if (unavailable) emptyMap() else it.currentMembers,
                        historyLoaded = if (unavailable) false else it.historyLoaded,
                    )
                }
                return@launch
            }
            val currentUser = getCurrentUserUseCase()
            val nextWeekStart = if (dayOnePlanningEnabled) "" else upcomingWeekStart(group, clock)
            _uiState.update {
                it.copy(
                    group = group,
                    currentUserId = currentUser?.userId.orEmpty(),
                    nextWeekStart = nextWeekStart,
                )
            }

            if (dayOnePlanningEnabled && currentUser != null) launch {
                observeNextWeekPlanUseCase.scheduledGoal(groupId)
                    .catch { _uiState.update { it.copy(scheduledGoal = null) } }
                    .collect { result ->
                        _uiState.update { it.copy(scheduledGoal = result.getOrNull()) }
                    }
            }

            // Names and opted-in photos are helpful but not required to explain
            // the group's week, so this stream never owns the screen's error.
            val membershipJob = launch {
                getGroupMembershipsUseCase(groupId)
                    .catch { emit(emptyList()) }
                    .collect { memberships ->
                        _uiState.update {
                            if (it.isGroupUnavailable) it
                            else it.copy(currentMembers = memberships.toDisplayMap())
                        }
                    }
            }

            if (circlePreviewEnabled || dayOnePlanningEnabled) {
                if (currentUser == null) {
                    val error = AppError.SessionExpired()
                    membershipJob.cancel()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = error,
                            groupAccess = GroupAccessState.Unavailable(error),
                        )
                    }
                    return@launch
                }

                observeGroupAccessUseCase(groupId, currentUser.userId)
                    .collectLatest { access ->
                        _uiState.update {
                            it.copy(
                                groupAccess = access,
                                isLoading = when {
                                    access is GroupAccessState.Loading -> true
                                    access.hasCircleAccess -> !it.historyLoaded
                                    else -> false
                                },
                                loadError = (access as? GroupAccessState.Unavailable)?.error,
                                weeks = if (access.hasCircleAccess) it.weeks else emptyList(),
                                historyLoaded = if (access.hasCircleAccess) {
                                    it.historyLoaded
                                } else {
                                    false
                                },
                                nextWeekPlan = if (access.hasNextWeekTogether) {
                                    it.nextWeekPlan
                                } else {
                                    null
                                },
                                myNextWeekPlanResponse = if (access.hasNextWeekTogether) {
                                    it.myNextWeekPlanResponse
                                } else {
                                    null
                                },
                                nextWeekPlanLoaded = if (access.hasNextWeekTogether) {
                                    it.nextWeekPlanLoaded
                                } else {
                                    false
                                },
                            )
                        }
                        if (access.hasCircleAccess) {
                            coroutineScope {
                                launch { collectHistory(membershipJob, accessGated = true) }
                                if (access.hasNextWeekTogether) {
                                    if (dayOnePlanningEnabled) launch {
                                        collectAuthoritativePlanning(group, currentUser.userId)
                                    } else {
                                      launch { collectNextWeekPlan(group, nextWeekStart) }
                                      launch {
                                        collectMyNextWeekResponse(
                                            group,
                                            nextWeekStart,
                                            currentUser.userId,
                                        )
                                      }
                                    }
                                }
                            }
                        }
                    }
            } else {
                collectHistory(membershipJob, accessGated = false)
            }
        }
    }

    fun saveNextWeekPlan(choice: NextWeekPlanChoice, customTarget: Int? = null) {
        val state = _uiState.value
        val group = state.group ?: return
        val source = state.weeks.firstOrNull()
        if (source == null && !dayOnePlanningEnabled) return
        if (!state.groupAccess.hasNextWeekTogether || !state.isOrganizer ||
            state.isOffline || state.isSavingNextWeekPlan || !state.nextWeekPlanLoaded
        ) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingNextWeekPlan = true, nextWeekPlanError = null) }
            saveNextWeekPlanUseCase(
                group = group,
                userId = state.currentUserId,
                weekStart = state.nextWeekStart,
                choice = choice,
                customTarget = customTarget,
                source = source,
                authoritative = dayOnePlanningEnabled,
            ).onFailure { throwable ->
                _uiState.update { it.copy(nextWeekPlanError = throwable.toAppError()) }
            }
            _uiState.update { it.copy(isSavingNextWeekPlan = false) }
        }
    }

    private suspend fun collectAuthoritativePlanning(group: Group, userId: String) = coroutineScope {
        var observedWeek: String? = null
        var planJob: Job? = null
        var responseJob: Job? = null
        while (isActive) {
            // collectAsStateWithLifecycle unsubscribes when the screen stops.
            // Do not keep making callable requests from a retained background VM.
            _uiState.subscriptionCount.first { it > 0 }
            val result = observeNextWeekPlanUseCase.planningWindow(group.groupId)
            val window = result.getOrNull()
            if (window == null) {
                planJob?.cancel()
                responseJob?.cancel()
                observedWeek = null
                _uiState.update { it.copy(nextWeekPlanLoaded = false,
                    nextWeekPlan = null, myNextWeekPlanResponse = null,
                    nextWeekPlanError = result.exceptionOrNull()?.toAppError()) }
            } else {
                _uiState.update { it.copy(nextWeekStart = window.weekStart,
                    planningTimeZone = window.timeZone,
                    group = it.group?.copy(weeklyTarget = window.currentTargetSteps,
                        weekStartDay = window.startDay)) }
                if (observedWeek != window.weekStart) {
                    planJob?.cancel()
                    responseJob?.cancel()
                    _uiState.update { it.copy(nextWeekPlan = null,
                        myNextWeekPlanResponse = null, nextWeekPlanLoaded = false,
                        nextWeekPlanError = null) }
                    observedWeek = window.weekStart
                    planJob = launch { collectNextWeekPlan(group, window.weekStart) }
                    responseJob = launch { collectMyNextWeekResponse(group, window.weekStart, userId) }
                }
            }
            // delay is monotonic. A changed device clock cannot extend the
            // server window; the callable/rules recheck every mutation too.
            delay(window?.remainingMillis?.coerceIn(250L, 60_000L) ?: 60_000L)
        }
    }

    fun saveNextWeekResponse(response: NextWeekResponse) {
        val state = _uiState.value
        if (!state.groupAccess.hasNextWeekTogether ||
            state.isOffline || state.isSavingNextWeekResponse ||
            state.currentUserId.isBlank() ||
            state.nextWeekPlan == null
        ) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingNextWeekResponse = true, nextWeekPlanError = null) }
            saveNextWeekResponseUseCase(
                groupId = groupId,
                weekStart = state.nextWeekStart,
                userId = state.currentUserId,
                response = response,
            ).onFailure { throwable ->
                _uiState.update { it.copy(nextWeekPlanError = throwable.toAppError()) }
            }
            _uiState.update { it.copy(isSavingNextWeekResponse = false) }
        }
    }

    private suspend fun collectNextWeekPlan(group: Group, weekStart: String) {
        observeNextWeekPlanUseCase(group.groupId, weekStart)
            .catch { throwable ->
                _uiState.update {
                    it.copy(
                        nextWeekPlanLoaded = true,
                        nextWeekPlanError = throwable.toAppError(),
                    )
                }
            }
            .collect { result ->
                result.fold(
                    onSuccess = { plan ->
                        _uiState.update {
                            it.copy(
                                nextWeekPlan = plan,
                                nextWeekPlanLoaded = true,
                                nextWeekPlanError = null,
                            )
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                nextWeekPlanLoaded = true,
                                nextWeekPlanError = throwable.toAppError(),
                            )
                        }
                    },
                )
            }
    }

    private suspend fun collectMyNextWeekResponse(
        group: Group,
        weekStart: String,
        userId: String,
    ) {
        observeMyNextWeekResponseUseCase(
            group.groupId,
            weekStart,
            userId,
        )
            .catch { throwable ->
                _uiState.update { it.copy(nextWeekPlanError = throwable.toAppError()) }
            }
            .collect { result ->
                result.fold(
                    onSuccess = { response ->
                        _uiState.update {
                            it.copy(myNextWeekPlanResponse = response)
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update { it.copy(nextWeekPlanError = throwable.toAppError()) }
                    },
                )
            }
    }

    private suspend fun collectHistory(
        membershipJob: Job,
        accessGated: Boolean,
    ) {
        observeGroupWeekSummariesUseCase(groupId)
            .catch { throwable ->
                val error = throwable.toAppError()
                // Under the preview rules, history can become forbidden at
                // expiry while the group and membership remain healthy.
                // Only the legacy ungated path may interpret that error as
                // the whole group disappearing.
                val unavailable = !accessGated && error.isGroupGone()
                val accessLost = accessGated && error is AppError.PermissionDenied
                if (unavailable) membershipJob.cancel()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = error,
                        isGroupUnavailable = unavailable,
                        group = if (unavailable) null else it.group,
                        weeks = if (unavailable || accessLost) emptyList() else it.weeks,
                        currentMembers = if (unavailable) emptyMap() else it.currentMembers,
                        historyLoaded = if (unavailable || accessLost) false else it.historyLoaded,
                    )
                }
            }
            .collect { weeks ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        historyLoaded = true,
                        loadError = null,
                        weeks = weeks,
                    )
                }
            }
    }
}

private fun List<Membership>.toDisplayMap(): Map<String, WeeklyReviewMember> =
    mapNotNull { membership ->
        membership.displayName
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { name ->
                membership.userId to WeeklyReviewMember(
                    displayName = name,
                    photoUrl = membership.photoUrl.takeIf { it.isNotBlank() },
                )
            }
    }.toMap()

private fun AppError.isGroupGone(): Boolean =
    this is AppError.NotFound || this is AppError.PermissionDenied

