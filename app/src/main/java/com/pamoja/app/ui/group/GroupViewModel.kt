package com.pamoja.app.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
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
import com.pamoja.app.domain.usecase.UpdateWeeklyTargetUseCase
import com.pamoja.app.util.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val isLoading: Boolean = false,
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
    private val userPreferences: UserPreferences,
    private val workManagerScheduler: WorkManagerScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = GroupUiState(isLoading = true)

            val currentUser = getCurrentUserUseCase()
            if (currentUser == null) {
                _uiState.value = GroupUiState(error = "User not found. Please sign in again.")
                return@launch
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

            observeMembersAndSteps(groupId, group, currentUser.userId)
        }
    }

    private fun observeMembersAndSteps(
        groupId: String,
        group: Group,
        currentUserId: String
    ) {
        viewModelScope.launch {
            getGroupMembersUseCase(groupId).collect { members ->
                if (members.isEmpty()) return@collect

                val memberIds = members.map { it.userId }
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

                getGroupStepsForWeekUseCase(memberIds).collect { weeklyEntries ->
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