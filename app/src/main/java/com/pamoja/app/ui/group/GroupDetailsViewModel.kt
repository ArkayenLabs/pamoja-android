package com.pamoja.app.ui.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.RemovedMember
import com.pamoja.app.domain.usecase.DeleteGroupUseCase
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.GetMembershipUseCase
import com.pamoja.app.domain.usecase.LeaveGroupUseCase
import com.pamoja.app.domain.usecase.LetMemberBackInUseCase
import com.pamoja.app.domain.usecase.ObserveGroupAccessUseCase
import com.pamoja.app.domain.usecase.ObserveRemovedMembersUseCase
import com.pamoja.app.domain.usecase.RemoveGroupMemberUseCase
import com.pamoja.app.domain.usecase.RemoveGroupPhotoUseCase
import com.pamoja.app.domain.usecase.UpdateGroupPhotoUseCase
import com.pamoja.app.domain.usecase.UpdateGroupSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class GroupDetailsUiState(
    val group: Group? = null,
    /** Admin first, then in the order people joined. */
    val members: List<Membership> = emptyList(),
    val currentUserId: String = "",
    /** Fails closed: nothing paid unlocks until the server has said so. */
    val access: GroupAccessState = GroupAccessState.Loading,
    val isOffline: Boolean = false,
    val isSaving: Boolean = false,
    /** One-shot: a setting was saved, so its sheet can close. */
    val settingsSaved: Boolean = false,
    val removingMemberId: String? = null,
    /** Admin only: people they removed, who the link no longer lets in. */
    val removed: List<RemovedMember> = emptyList(),
    val lettingBackInId: String? = null,
    val isUploadingPhoto: Boolean = false,
    val isLeaving: Boolean = false,
    val isDeleting: Boolean = false,
    /** Nothing loaded at all. */
    val fatalError: AppError? = null,
    /** Deleted, or no longer ours to see. Terminal: Retry cannot fix it. */
    val isGroupUnavailable: Boolean = false,
    /** A failed action over a loaded screen. Snackbar only. */
    val actionError: AppError? = null,
    /** One-shot: this person is no longer in the group, by leaving or deleting it. */
    val isGone: Boolean = false,
) {
    val isAdmin: Boolean get() = group != null && group.adminId == currentUserId

    /** Never below two, and never below the people already here. */
    val minSelectableCap: Int
        get() = maxOf(UpdateGroupSettingsUseCase.MIN_MEMBER_CAP, group?.memberCount ?: 0)

    /** A saved plan fixes the week start until it has been applied. */
    val weekStartLocked: Boolean get() = !group?.plannedWeekStart.isNullOrBlank()

    val isFull: Boolean get() = group != null && group.maxMemberCap > 0 && group.memberCount >= group.maxMemberCap
}

/**
 * Group details: who is in the group, its settings, Premium, and leaving.
 *
 * Replaces the old edit form. Each setting saves on its own from a sheet, so
 * there is no draft to reconcile against the live document: the screen always
 * shows what is stored, and a member joining mid-edit cannot overwrite a
 * half-typed change.
 */
@HiltViewModel
class GroupDetailsViewModel @Inject constructor(
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupMembershipsUseCase: GetGroupMembershipsUseCase,
    private val getMembershipUseCase: GetMembershipUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val observeGroupAccessUseCase: ObserveGroupAccessUseCase,
    private val updateGroupSettingsUseCase: UpdateGroupSettingsUseCase,
    private val removeGroupMemberUseCase: RemoveGroupMemberUseCase,
    private val updateGroupPhotoUseCase: UpdateGroupPhotoUseCase,
    private val removeGroupPhotoUseCase: RemoveGroupPhotoUseCase,
    private val leaveGroupUseCase: LeaveGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val observeRemovedMembersUseCase: ObserveRemovedMembersUseCase,
    private val letMemberBackInUseCase: LetMemberBackInUseCase,
    connectivityObserver: ConnectivityObserver,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()

    private val _uiState = MutableStateFlow(GroupDetailsUiState())
    val uiState: StateFlow<GroupDetailsUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var accessJob: Job? = null
    private var removedJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = _uiState.value.copy(fatalError = AppError.SessionExpired())
                return@launch
            }
            _uiState.value = _uiState.value.copy(currentUserId = user.userId, fatalError = null)
            observe()

            val membership = getMembershipUseCase(user.userId, groupId).getOrNull()
                ?.takeIf { it.userId == user.userId && it.groupId == groupId }
            if (membership == null) {
                _uiState.value = _uiState.value.copy(access = GroupAccessState.Free)
            } else {
                accessJob?.cancel()
                accessJob = viewModelScope.launch {
                    observeGroupAccessUseCase(groupId, user.userId).collect { access ->
                        _uiState.value = _uiState.value.copy(access = access)
                    }
                }
            }
        }
    }

    /** Live, from the local cache first, like Home. */
    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(getGroupUseCase.observe(groupId), getGroupMembershipsUseCase(groupId)) { group, members ->
                group to members
            }
                .catch { error ->
                    // Leaving or deleting ends access mid-listen. That is the
                    // action succeeding, not the screen failing.
                    if (_uiState.value.isLeaving || _uiState.value.isDeleting || _uiState.value.isGone) {
                        return@catch
                    }
                    val appError = error.toAppError()
                    _uiState.value = _uiState.value.copy(
                        fatalError = appError,
                        isGroupUnavailable = appError is AppError.NotFound || appError is AppError.PermissionDenied,
                    )
                }
                .collect { (group, members) ->
                    _uiState.value = _uiState.value.copy(
                        group = group,
                        members = members.sortedWith(
                            compareByDescending<Membership> { it.userId == group.adminId }
                                .thenBy { it.joinedAt }
                                .thenBy { it.displayName },
                        ),
                        fatalError = null,
                    )
                    watchRemovedWhileAdmin(group.adminId == _uiState.value.currentUserId)
                }
        }
    }

    /**
     * Only the admin may read the removed list, so it is watched only while
     * this person is the admin (a handover can change that mid-screen).
     */
    private fun watchRemovedWhileAdmin(isAdmin: Boolean) {
        if (isAdmin == (removedJob != null)) return
        removedJob?.cancel()
        removedJob = null
        if (!isAdmin) {
            _uiState.value = _uiState.value.copy(removed = emptyList())
            return
        }
        removedJob = viewModelScope.launch {
            observeRemovedMembersUseCase(groupId)
                // A list nobody else can see; failing to load it is not worth
                // a screen error, and the rules still keep those people out.
                .catch { emit(emptyList()) }
                .collect { _uiState.value = _uiState.value.copy(removed = it) }
        }
    }

    fun retry() = load()

    fun saveName(name: String) = saveSettings { it.copy(name = name) }

    fun saveWeeklyTarget(target: Int) = saveSettings { it.copy(weeklyTarget = target) }

    fun saveMemberCap(cap: Int) = saveSettings { it.copy(maxMemberCap = cap) }

    fun saveWeekStart(day: DayOfWeek) {
        if (_uiState.value.weekStartLocked) return
        saveSettings { it.copy(weekStartDay = day.name) }
    }

    /**
     * One setting at a time, with every other field sent exactly as stored.
     * The use case validates the whole set, as creation does.
     */
    private fun saveSettings(change: (Group) -> Group) {
        val state = _uiState.value
        val group = state.group ?: return
        if (!state.isAdmin || state.isSaving || state.isOffline) return
        val next = change(group)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, actionError = null)
            updateGroupSettingsUseCase(
                group = group,
                editorId = state.currentUserId,
                name = next.name,
                weeklyTarget = next.weeklyTarget,
                maxMemberCap = next.maxMemberCap,
                weekStartDay = next.startDay,
            ).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isSaving = false, settingsSaved = true) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isSaving = false, actionError = e.toAppError())
                },
            )
        }
    }

    fun consumeSettingsSaved() {
        _uiState.value = _uiState.value.copy(settingsSaved = false)
    }

    fun removeMember(memberId: String) {
        val state = _uiState.value
        val group = state.group ?: return
        if (state.removingMemberId != null || state.isOffline) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(removingMemberId = memberId, actionError = null)
            val result = removeGroupMemberUseCase(group, state.currentUserId, memberId)
            _uiState.value = _uiState.value.copy(
                removingMemberId = null,
                actionError = result.exceptionOrNull()?.toAppError(),
            )
        }
    }

    fun letBackIn(userId: String) {
        val state = _uiState.value
        val group = state.group ?: return
        if (state.lettingBackInId != null || state.isOffline) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(lettingBackInId = userId, actionError = null)
            val result = letMemberBackInUseCase(group, state.currentUserId, userId)
            _uiState.value = _uiState.value.copy(
                lettingBackInId = null,
                actionError = result.exceptionOrNull()?.toAppError(),
            )
        }
    }

    /** Applies at once: a picked photo has to be uploaded to be shown at all. */
    fun onPhotoPicked(imageUri: String) {
        val state = _uiState.value
        val group = state.group ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true, actionError = null)
            val result = updateGroupPhotoUseCase(group, state.currentUserId, imageUri)
            _uiState.value = _uiState.value.copy(
                isUploadingPhoto = false,
                actionError = result.exceptionOrNull()?.toAppError(),
            )
        }
    }

    fun removePhoto() {
        val state = _uiState.value
        val group = state.group ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true, actionError = null)
            val result = removeGroupPhotoUseCase(group, state.currentUserId)
            _uiState.value = _uiState.value.copy(
                isUploadingPhoto = false,
                actionError = result.exceptionOrNull()?.toAppError(),
            )
        }
    }

    fun leaveGroup() {
        val state = _uiState.value
        val group = state.group ?: return
        if (state.isAdmin || state.isLeaving || state.isOffline) return
        _uiState.value = state.copy(isLeaving = true, actionError = null)
        viewModelScope.launch {
            leaveGroupUseCase(group).fold(
                onSuccess = { finish() },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLeaving = false, actionError = e.toAppError())
                },
            )
        }
    }

    fun deleteGroup() {
        val state = _uiState.value
        val group = state.group ?: return
        if (!state.isAdmin || state.isDeleting || state.isOffline) return
        _uiState.value = state.copy(isDeleting = true, actionError = null)
        viewModelScope.launch {
            deleteGroupUseCase(group, state.currentUserId).fold(
                onSuccess = { finish() },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isDeleting = false, actionError = e.toAppError())
                },
            )
        }
    }

    private fun finish() {
        observeJob?.cancel()
        accessJob?.cancel()
        removedJob?.cancel()
        _uiState.value = _uiState.value.copy(isLeaving = false, isDeleting = false, isGone = true)
    }

    fun clearActionError() {
        _uiState.value = _uiState.value.copy(actionError = null)
    }
}
