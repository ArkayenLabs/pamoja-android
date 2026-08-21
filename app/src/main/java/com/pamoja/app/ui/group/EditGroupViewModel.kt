package com.pamoja.app.ui.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.StepGoal
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembersUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.RemoveGroupMemberUseCase
import com.pamoja.app.domain.usecase.RemoveGroupPhotoUseCase
import com.pamoja.app.domain.usecase.UpdateGroupPhotoUseCase
import com.pamoja.app.domain.usecase.UpdateGroupSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class EditGroupUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val isOffline: Boolean = false,

    /** Null until the group has loaded. Everything below is a draft over it. */
    val group: Group? = null,
    val currentUserId: String = "",

    val name: String = "",
    /** The setting. The group total is derived from it, never edited directly. */
    val dailyPerPersonTarget: Int = StepGoal.DEFAULT_DAILY_PER_PERSON,
    val maxMemberCap: Int = 10,
    val canMembersEditTarget: Boolean = false,
    val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,

    /** Everyone in the group, so the admin can remove someone. */
    val members: List<User> = emptyList(),
    /** Set while a specific removal is in flight, to disable just that row. */
    val removingMemberId: String? = null,

    /** The group's photo, blank when it has none. */
    val photoUrl: String = "",
    val isUploadingPhoto: Boolean = false,

    val error: AppError? = null,
    val isSaved: Boolean = false,
) {
    val isAdmin: Boolean get() = group != null && group.adminId == currentUserId

    /**
     * The floor the cap slider may not go below.
     *
     * Never below two, and never below the people already here, because
     * lowering the cap does not remove anyone.
     */
    val minSelectableCap: Int
        get() = maxOf(UpdateGroupSettingsUseCase.MIN_MEMBER_CAP, group?.memberCount ?: 0)

    /** Nothing to save, so the button should not invite a pointless write. */
    val isDirty: Boolean
        get() = group?.let {
            name.trim() != it.name ||
                dailyPerPersonTarget != it.effectiveDailyPerPerson ||
                maxMemberCap != it.maxMemberCap ||
                canMembersEditTarget != it.canMembersEditTarget ||
                weekStartDay != it.startDay
        } ?: false

    val canSave: Boolean
        get() = isAdmin && isDirty && !isSaving && !isOffline && name.isNotBlank()
}

/**
 * Editing an existing group.
 *
 * Separate from [CreateGroupViewModel] rather than a mode flag on it. Creation
 * validates a blank slate and navigates onward; this one has to reconcile a
 * draft against a live document, knows about the members already in the group,
 * and can only be used by one person.
 */
@HiltViewModel
class EditGroupViewModel @Inject constructor(
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val updateGroupSettingsUseCase: UpdateGroupSettingsUseCase,
    private val removeGroupMemberUseCase: RemoveGroupMemberUseCase,
    private val updateGroupPhotoUseCase: UpdateGroupPhotoUseCase,
    private val removeGroupPhotoUseCase: RemoveGroupPhotoUseCase,
    private val connectivityObserver: ConnectivityObserver,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()

    private val _uiState = MutableStateFlow(EditGroupUiState())
    val uiState: StateFlow<EditGroupUiState> = _uiState.asStateFlow()

    init {
        observeConnectivity()
        load()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasLoadedOnce = true,
                    error = AppError.SessionExpired(),
                )
                return@launch
            }

            getGroupUseCase(groupId).fold(
                onSuccess = { group ->
                    // The draft is seeded from the document once. Later
                    // snapshots must not overwrite it, or a member joining
                    // mid-edit would silently discard what was being typed.
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        group = group,
                        currentUserId = user.userId,
                        name = group.name,
                        // effective, not raw: a legacy group has no
                        // per-person figure stored, so this back-computes the
                        // one its total implies rather than showing zero.
                        dailyPerPersonTarget = group.effectiveDailyPerPerson,
                        maxMemberCap = group.maxMemberCap,
                        canMembersEditTarget = group.canMembersEditTarget,
                        weekStartDay = group.startDay,
                        photoUrl = group.photoUrl,
                        error = null,
                    )
                    observeMembers()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    /**
     * Members stay live while the screen is open.
     *
     * Only the list is updated from this, never the draft fields, so someone
     * joining or leaving refreshes the roster without touching what the admin
     * is editing.
     */
    private fun observeMembers() {
        viewModelScope.launch {
            getGroupMembersUseCase(groupId)
                .catch { /* The roster is secondary; a failure here must not
                            replace a loaded editor with an error screen. */ }
                .collect { members ->
                    _uiState.value = _uiState.value.copy(members = members)
                }
        }
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    fun onDailyPerPersonChange(value: Int) {
        _uiState.value = _uiState.value.copy(
            dailyPerPersonTarget = value.coerceIn(
                StepGoal.MIN_DAILY_PER_PERSON,
                StepGoal.MAX_DAILY_PER_PERSON,
            )
        )
    }

    fun onMemberCapChange(value: Int) {
        // Clamped here as well as validated on save, so the control cannot even
        // be dragged to a number that would be rejected.
        val floor = _uiState.value.minSelectableCap
        _uiState.value = _uiState.value.copy(
            maxMemberCap = value.coerceIn(floor, UpdateGroupSettingsUseCase.MAX_MEMBER_CAP)
        )
    }

    fun onMembersEditTargetChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(canMembersEditTarget = value)
    }

    fun onWeekStartDayChange(value: DayOfWeek) {
        _uiState.value = _uiState.value.copy(weekStartDay = value)
    }

    fun save() {
        val state = _uiState.value
        val group = state.group ?: return
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null)

            updateGroupSettingsUseCase(
                group = group,
                editorId = state.currentUserId,
                name = state.name,
                dailyPerPersonTarget = state.dailyPerPersonTarget,
                maxMemberCap = state.maxMemberCap,
                canMembersEditTarget = state.canMembersEditTarget,
                weekStartDay = state.weekStartDay,
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    fun removeMember(memberId: String) {
        val state = _uiState.value
        val group = state.group ?: return

        viewModelScope.launch {
            _uiState.value = state.copy(removingMemberId = memberId, error = null)

            removeGroupMemberUseCase(
                group = group,
                editorId = state.currentUserId,
                memberId = memberId,
            ).fold(
                onSuccess = {
                    // memberCount moved, so the cap floor moved with it. The
                    // group is re-read rather than patched locally, since the
                    // floor is derived from it and a stale count would let the
                    // cap be dragged below the real headcount.
                    getGroupUseCase(groupId).onSuccess { fresh ->
                        _uiState.value = _uiState.value.copy(group = fresh)
                    }
                    _uiState.value = _uiState.value.copy(removingMemberId = null)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        removingMemberId = null,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    /**
     * Applies immediately, like the profile photo and unlike the text fields.
     *
     * A picked image has to be uploaded to be shown at all, so there is nothing
     * to defer to Save. Deferring would also mean an abandoned edit leaves an
     * uploaded object nothing points at.
     */
    fun onPhotoPicked(imageUri: String) {
        val state = _uiState.value
        val group = state.group ?: return

        viewModelScope.launch {
            _uiState.value = state.copy(isUploadingPhoto = true, error = null)

            updateGroupPhotoUseCase(group, state.currentUserId, imageUri).fold(
                onSuccess = { url ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        photoUrl = url,
                        group = group.copy(photoUrl = url),
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    fun onPhotoRemoved() {
        val state = _uiState.value
        val group = state.group ?: return

        viewModelScope.launch {
            _uiState.value = state.copy(isUploadingPhoto = true, error = null)

            removeGroupPhotoUseCase(group, state.currentUserId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        photoUrl = "",
                        group = group.copy(photoUrl = ""),
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        load()
    }

    /** The week-start options offered, matching creation. */
    val weekStartOptions: List<DayOfWeek> = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)

    /** Used only to label the default in copy, never to override a stored value. */
    val localeDefaultStartDay: DayOfWeek = WeekWindow.localeDefault()
}
