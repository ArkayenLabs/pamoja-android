package com.pamoja.app.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.StepEntry
import kotlinx.coroutines.Job
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembersUseCase
import com.pamoja.app.domain.usecase.GetGroupStepsForWeekUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.GetMembershipUseCase
import com.pamoja.app.domain.usecase.GetStepsForUserUseCase
import com.pamoja.app.domain.usecase.UpdateWeeklyTargetUseCase
import com.pamoja.app.domain.usecase.SyncTodayStepsUseCase
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.util.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val error: String? = null,
    val group: Group? = null,
    val membership: Membership? = null,
    val currentUserId: String = "",
    val memberStepData: List<MemberStepData> = emptyList(),
    val combinedWeeklySteps: Long = 0L,
    val isAdmin: Boolean = false
)


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
    // Exposed so the dashboard's "enable step tracking" card can drive the real
    // Health Connect permission flow. It previously requested ACTIVITY_RECOGNITION,
    // which is not declared in the manifest and is not the permission this app
    // uses, so the card could never succeed.
    val healthConnectReader: HealthConnectReader,
    private val workManagerScheduler: WorkManagerScheduler,
    private val analyticsManager: AnalyticsManager,
    private val syncTodayStepsUseCase: SyncTodayStepsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    /** Prevents duplicate weekly_goal_reached events during a single screen session. */
    private var weeklyGoalLoggedThisSession = false

    /** Guards against duplicate loadGroup calls launching multiple observers. */
    private var observeJob: Job? = null

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = GroupUiState(isLoading = true)

            val currentUser = getCurrentUserUseCase()
            if (currentUser == null) {
                _uiState.value = GroupUiState(error = "User not found. Please sign in again.")
                return@launch
            }
            // Immediately sync steps on screen open, don't wait for WorkManager
            viewModelScope.launch {
                syncTodayStepsUseCase(currentUser.userId)
            }

            val groupResult = getGroupUseCase(groupId)
            val group = groupResult.getOrElse {
                _uiState.value = GroupUiState(error = it.message ?: "Failed to load group")
                return@launch
            }

            val membershipResult = getMembershipUseCase(currentUser.userId, groupId)
            val membership = membershipResult.getOrNull()

            val isAdmin = group.adminId == currentUser.userId

            _uiState.value = GroupUiState(
                group = group,
                membership = membership,
                currentUserId = currentUser.userId,
                isAdmin = isAdmin
            )

            workManagerScheduler.scheduleStepSync()
            userPreferences.saveActiveGroupId(groupId)

            analyticsManager.logGroupScreenViewed(groupId)

            observeMembersAndSteps(groupId, group, currentUser.userId)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeMembersAndSteps(
        groupId: String,
        group: Group,
        currentUserId: String
    ) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            getGroupMembersUseCase(groupId)
                .flatMapLatest { members ->
                    if (members.isEmpty()) {
                        flowOf(Pair(emptyList<User>(), emptyList<StepEntry>()))
                    } else {
                        val memberIds = members.map { it.userId }
                        getGroupStepsForWeekUseCase(memberIds)
                            .map { weeklyEntries -> Pair(members, weeklyEntries) }
                    }
                }
                .catch { error ->
                    val msg = if (error.message?.contains("index") == true) {
                        "Database setup required: Please click the index link in your Logcat to generate the required Firestore index."
                    } else {
                        error.message
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                }
                .collect { (members, weeklyEntries) ->
                    if (members.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            memberStepData = emptyList()
                        )
                        return@collect
                    }

                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

                    val memberStepData = members.map { member ->
                        val todayEntry = weeklyEntries.find {
                            it.userId == member.userId && it.date == today
                        }
                        val weeklyTotal = weeklyEntries
                            .filter { it.userId == member.userId }
                            .sumOf { it.stepCount }

                        MemberStepData(
                            user = member,
                            todaySteps = todayEntry?.stepCount ?: 0L,
                            weeklySteps = weeklyTotal
                        )
                    }.sortedByDescending { it.todaySteps }

                    val combinedWeekly = memberStepData.sumOf { it.weeklySteps }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        memberStepData = memberStepData,
                        combinedWeeklySteps = combinedWeekly
                    )

                    // Log weekly goal reached, once per screen session
                    val target = group.weeklyTarget
                    if (combinedWeekly >= target && !weeklyGoalLoggedThisSession) {
                        weeklyGoalLoggedThisSession = true
                        analyticsManager.logWeeklyGoalReached(groupId, combinedWeekly, target)
                    }
                }
        }
    }

    fun updateWeeklyTarget(target: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val group = state.group ?: return@launch
            val membership = state.membership ?: return@launch

            val result = updateWeeklyTargetUseCase(
                groupId = group.groupId,
                target = target,
                userId = state.currentUserId,
                group = group
            )

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        group = group.copy(weeklyTarget = target)
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to update target"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}