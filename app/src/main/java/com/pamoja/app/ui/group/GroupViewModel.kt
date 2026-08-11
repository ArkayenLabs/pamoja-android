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
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembersUseCase
import com.pamoja.app.domain.usecase.GetGroupStepsForWeekUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.GetMembershipUseCase
import com.pamoja.app.domain.usecase.GetStepsForUserUseCase
import com.pamoja.app.domain.usecase.SyncTodayStepsUseCase
import com.pamoja.app.domain.usecase.UpdateWeeklyTargetUseCase
import com.pamoja.app.util.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    private val getGroupStepsForWeekUseCase: GetGroupStepsForWeekUseCase,
    private val getStepsForUserUseCase: GetStepsForUserUseCase,
    private val getMembershipUseCase: GetMembershipUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val updateWeeklyTargetUseCase: UpdateWeeklyTargetUseCase,
    val userPreferences: UserPreferences,
    val healthConnectReader: HealthConnectReader,
    private val workManagerScheduler: WorkManagerScheduler,
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

            workManagerScheduler.scheduleStepSync()
            userPreferences.saveActiveGroupId(groupId)
            analyticsManager.logGroupScreenViewed(groupId)

            observeMembersAndSteps(groupId, group)
        }
    }

    fun retry() {
        currentGroupId?.let { loadGroup(it) }
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeMembersAndSteps(groupId: String, group: Group) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            getGroupMembersUseCase(groupId)
                .flatMapLatest { members ->
                    if (members.isEmpty()) {
                        flowOf(members to Result.success(emptyList<StepEntry>()))
                    } else {
                        getGroupStepsForWeekUseCase(members.map { it.userId })
                            .map { entries -> members to Result.success(entries) }
                            // Caught INSIDE the inner flow, so a steps failure
                            // does not tear down the outer members flow with it.
                            // This is what makes "names but no numbers" possible
                            // instead of losing the whole screen. Still one
                            // collect: no nested collector is introduced.
                            .catch { error -> emit(members to Result.failure(error)) }
                    }
                }
                .catch { error ->
                    // Only reached when MEMBERS fail, which does cost us the screen.
                    val appError = error.toAppError()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        fatalError = appError,
                        isGroupUnavailable = appError.isGroupGone(),
                    )
                }
                .collect { (members, stepsResult) ->
                    val entries = stepsResult.getOrNull().orEmpty()
                    val stepsError = stepsResult.exceptionOrNull()?.toAppError()

                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

                    val memberStepData = members.map { member ->
                        val memberEntries = entries.filter { it.userId == member.userId }
                        MemberStepData(
                            user = member,
                            todaySteps = memberEntries.find { it.date == today }?.stepCount ?: 0L,
                            weeklySteps = memberEntries.sumOf { it.stepCount },
                        )
                    }.sortedByDescending { it.todaySteps }

                    val combinedWeekly = memberStepData.sumOf { it.weeklySteps }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedMembers = true,
                        memberStepData = memberStepData,
                        combinedWeeklySteps = combinedWeekly,
                        stepsError = stepsError,
                    )

                    // Not logged when the step read failed, since a total of zero
                    // would otherwise look like a real result.
                    if (stepsError == null &&
                        combinedWeekly >= group.weeklyTarget &&
                        !weeklyGoalLoggedThisSession
                    ) {
                        weeklyGoalLoggedThisSession = true
                        analyticsManager.logWeeklyGoalReached(
                            groupId, combinedWeekly, group.weeklyTarget
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
