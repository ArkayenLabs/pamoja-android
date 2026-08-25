package com.pamoja.app.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.GetMembershipUseCase
import com.pamoja.app.domain.usecase.GetStepsForUserUseCase
import com.pamoja.app.domain.usecase.SyncTodayStepsUseCase
import com.pamoja.app.domain.usecase.UpdateWeeklyTargetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MemberStepData(
    val user: User,
    val todaySteps: Long,
    val weeklySteps: Long
)

data class GroupUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val membership: Membership? = null,
    val currentUserId: String = "",
    val memberStepData: List<MemberStepData> = emptyList(),
    val combinedWeeklySteps: Long = 0L,
    val isAdmin: Boolean = false,
    val isOffline: Boolean = false,
    /**
     * When steps last reached this phone, as epoch millis. Zero means never.
     *
     * Shown only while offline, and only where a number could be out of date.
     * A figure with no timestamp reads as current, so an offline screen without
     * one is quietly asserting something it cannot know.
     */
    val lastSyncedAt: Long = 0L,
    /**
     * Drives the pull-to-refresh spinner only, never the skeleton. A pull has
     * content on screen already, and swapping it for a skeleton would throw
     * away what the user is looking at to show them less.
     */
    val isRefreshing: Boolean = false,

    /**
     * Stopped the screen loading at all. Renders as the whole screen, because
     * there is nothing behind it to show.
     */
    val fatalError: AppError? = null,

    /**
     * The group loaded but its steps did not.
     *
     * Kept separate from [fatalError] because a leaderboard with names and no
     * numbers is still worth showing, and throwing the whole screen away for it
     * would be a worse answer than admitting one part is missing.
     */
    val stepsError: AppError? = null,

    /** Transient failures from actions, e.g. changing the target. Snackbar only. */
    val actionError: AppError? = null,

    /** The group is gone, or we are no longer allowed to see it. Terminal. */
    val isGroupUnavailable: Boolean = false,

    /** True once the member list has arrived at least once. */
    val hasLoadedMembers: Boolean = false,
) {
    val hasContent: Boolean get() = group != null && hasLoadedMembers

    /** Everyone in the group is on zero. Hopeful, not an error. */
    val showNoStepsYet: Boolean
        get() = hasLoadedMembers &&
            memberStepData.isNotEmpty() &&
            combinedWeeklySteps == 0L &&
            stepsError == null

    val showSkeleton: Boolean get() = isLoading && group == null
}

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupMembershipsUseCase: GetGroupMembershipsUseCase,
    private val getStepsForUserUseCase: GetStepsForUserUseCase,
    private val getMembershipUseCase: GetMembershipUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val updateWeeklyTargetUseCase: UpdateWeeklyTargetUseCase,
    val userPreferences: UserPreferences,
    val healthConnectReader: HealthConnectReader,
    private val analyticsManager: AnalyticsManager,
    private val syncTodayStepsUseCase: SyncTodayStepsUseCase,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    /** Prevents duplicate weekly_goal_reached events during a single screen session. */
    private var weeklyGoalLoggedThisSession = false

    /** Guards against duplicate loadGroup calls launching multiple observers. */
    private var observeJob: Job? = null

    private var currentGroupId: String? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
        // Written by StepSyncWorker on every successful run, so it already
        // means exactly what the offline states need: the last moment these
        // figures were known to be true.
        viewModelScope.launch {
            userPreferences.lastSyncTime.collect { at ->
                _uiState.value = _uiState.value.copy(lastSyncedAt = at)
            }
        }
    }

    fun loadGroup(groupId: String) {
        currentGroupId = groupId
        viewModelScope.launch {
            // Clears the previous attempt's errors without discarding connectivity
            // or anything else already resolved.
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                fatalError = null,
                stepsError = null,
                isGroupUnavailable = false,
            )

            val currentUser = getCurrentUserUseCase()
            if (currentUser == null) {
                // isLoading explicitly false. Setting only the error used to
                // leave the default isLoading = true in place, and since the
                // screen checks loading first, every failure rendered as a
                // spinner that never stopped.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    fatalError = AppError.SessionExpired(),
                )
                return@launch
            }

            // Fire and forget: a sync failure must not block the screen, and the
            // leaderboard renders from Firestore regardless.
            viewModelScope.launch { syncTodayStepsUseCase(currentUser.userId) }

            val group = getGroupUseCase(groupId).getOrElse { error ->
                val appError = error.toAppError()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    fatalError = appError,
                    isGroupUnavailable = appError.isGroupGone(),
                )
                return@launch
            }

            val membership = getMembershipUseCase(currentUser.userId, groupId).getOrNull()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                group = group,
                membership = membership,
                currentUserId = currentUser.userId,
                isAdmin = group.adminId == currentUser.userId,
            )

            userPreferences.saveActiveGroupId(groupId)
            analyticsManager.logGroupScreenViewed(groupId)

            observeMembersAndSteps(groupId, group)
        }
    }

    fun retry() {
        currentGroupId?.let { loadGroup(it) }
    }

    /**
     * Pull to refresh.
     *
     * Members and steps arrive over Firestore snapshot listeners, so this
     * seldom changes anything. It stays because it is the gesture people use
     * when a leaderboard looks stale, and because it recovers a partial
     * failure, steps that did not load beside members that did, without
     * throwing away the half that worked.
     */
    fun refresh() {
        val groupId = currentGroupId ?: return
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            loadGroup(groupId)
            // Held briefly even when the answer is instant: a spinner that
            // flickers and vanishes reads as one that never ran.
            delay(REFRESH_SPINNER_MIN_MS)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    /**
     * Re-checks Health Connect against the platform rather than trusting the
     * cached flag.
     *
     * The flag is only written when our own permission dialog returns, so a user
     * who revokes access in Health Connect settings while the app is in the
     * background leaves it stuck at granted. The prompt then never reappears and
     * their steps silently stop, which is both a bad experience and a Play
     * requirement we would be failing.
     */
    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val granted = runCatching {
                healthConnectReader.isAvailable() && healthConnectReader.hasPermission()
            }.getOrDefault(false)

            // Written to DataStore rather than mirrored into UiState. The screen
            // and StepSyncWorker both already read the flag from there, and a
            // second copy in UiState would be one more thing to drift.
            userPreferences.setHealthConnectGranted(granted)
        }
    }

    private fun observeMembersAndSteps(groupId: String, group: Group) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            // One flow, not two chained ones. The step figures now travel on the
            // membership documents, so the members list and their numbers arrive
            // in the same snapshot. That removes the inner flow this used to
            // flatMapLatest into, and with it the nested-collect hazard the
            // chained version had to be careful about.
            //
            // It also removes the "names but no numbers" partial state: there is
            // no longer a separate read that can fail on its own. Either the
            // memberships arrive or they do not.
            getGroupMembershipsUseCase(groupId)
                .catch { error ->
                    val appError = error.toAppError()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        fatalError = appError,
                        isGroupUnavailable = appError.isGroupGone(),
                    )
                }
                .collect { memberships ->
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val thisWeek = WeekWindow.startOf(group.startDay)

                    val memberStepData = memberships.map { membership ->
                        MemberStepData(
                            user = User(
                                userId = membership.userId,
                                name = membership.displayName,
                                photoUrl = membership.photoUrl.takeIf { it.isNotBlank() },
                            ),
                            // Both figures are read only when their own marker
                            // says they belong to now. A member whose device has
                            // not synced since yesterday, or since last week,
                            // carries a stale number, and showing that as current
                            // is the one way this denormalisation can lie.
                            todaySteps = membership.todaySteps
                                .takeIf { membership.todayDate == today } ?: 0L,
                            weeklySteps = membership.weeklySteps
                                .takeIf { membership.weekStart == thisWeek } ?: 0L,
                        )
                    }.sortedByDescending { it.todaySteps }

                    val combinedWeekly = memberStepData.sumOf { it.weeklySteps }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedMembers = true,
                        memberStepData = memberStepData,
                        combinedWeeklySteps = combinedWeekly,
                        stepsError = null,
                    )

                    if (combinedWeekly >= group.weeklyTarget &&
                        !weeklyGoalLoggedThisSession
                    ) {
                        weeklyGoalLoggedThisSession = true
                        analyticsManager.logWeeklyGoalReached(
                            groupId, group.weeklyTarget
                        )
                    }
                }
        }
    }

    fun updateWeeklyTarget(target: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val group = state.group ?: return@launch
            state.membership ?: return@launch

            updateWeeklyTargetUseCase(
                groupId = group.groupId,
                target = target,
                userId = state.currentUserId,
                group = group,
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(group = group.copy(weeklyTarget = target))
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(actionError = error.toAppError())
                }
            )
        }
    }

    fun clearActionError() {
        _uiState.value = _uiState.value.copy(actionError = null)
    }

    private companion object {
        /** Long enough that the gesture is acknowledged, short enough not to stall. */
        const val REFRESH_SPINNER_MIN_MS = 450L
    }
}

/**
 * Whether this failure means the group is gone for us.
 *
 * A deleted group and one we have been removed from are indistinguishable from
 * the client: the rules deny the read either way. Both are terminal, so both
 * get the same dead end with a route back rather than a Retry that will keep
 * failing.
 */
private fun AppError.isGroupGone(): Boolean =
    this is AppError.NotFound || this is AppError.PermissionDenied
