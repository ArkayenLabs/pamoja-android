package com.pamoja.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetMembershipUseCase
import com.pamoja.app.domain.usecase.JoinGroupUseCase
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.util.InviteLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateOrJoinUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val joinedGroupId: String? = null,
    /**
     * True when [joinedGroupId] was already joined rather than newly joined.
     * Opening your own invite link is legitimate, it should just say so instead
     * of silently landing you in the group as if something happened.
     */
    val wasAlreadyMember: Boolean = false
)

@HiltViewModel
class CreateOrJoinViewModel @Inject constructor(
    private val joinGroupUseCase: JoinGroupUseCase,
    private val getMembershipUseCase: GetMembershipUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val analyticsManager: AnalyticsManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateOrJoinUiState())
    val uiState: StateFlow<CreateOrJoinUiState> = _uiState.asStateFlow()

    init {
        consumePendingInvite()
    }

    /**
     * Honours an invite captured from a link or QR before the user was able to
     * act on it, typically because they had not finished onboarding yet.
     *
     * This view model is created when Home is shown, which is exactly the point
     * at which the user is signed in and the join can succeed.
     */
    private fun consumePendingInvite() {
        viewModelScope.launch {
            val code = userPreferences.pendingInviteCode.firstOrNull()
            if (code.isNullOrBlank()) return@launch

            // Cleared before attempting, so a persistent failure (revoked link,
            // full group) cannot trap the user in a retry loop on every launch.
            userPreferences.clearPendingInviteCode()
            joinGroup(code)
        }
    }

    /**
     * Joins a group from any accepted input: a verified https invite link, the
     * legacy pamoja:// link, or a bare invite code typed by hand.
     */
    fun joinGroup(rawLinkOrCode: String) {
        viewModelScope.launch {
            val code = InviteLink.parseCode(rawLinkOrCode) ?: rawLinkOrCode.trim()
            if (code.isBlank()) {
                _uiState.value = CreateOrJoinUiState(error = "That invite link does not look right.")
                return@launch
            }

            _uiState.value = CreateOrJoinUiState(isLoading = true)

            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = CreateOrJoinUiState(error = "User not found. Please sign in again.")
                return@launch
            }

            // Checked before joining so we can tell "you just joined" apart from
            // "you were already in this group". The join itself is idempotent,
            // so without this the two outcomes are indistinguishable and the
            // user gets no feedback at all.
            val alreadyMember = getMembershipUseCase(user.userId, code).getOrNull() != null

            val result = joinGroupUseCase(code, user.userId)
            result.fold(
                onSuccess = {
                    _uiState.value = CreateOrJoinUiState(
                        joinedGroupId = code,
                        wasAlreadyMember = alreadyMember,
                    )
                    // Only count a genuine join. Otherwise opening your own link
                    // repeatedly would inflate the invite conversion metric.
                    if (!alreadyMember) {
                        analyticsManager.logInviteLinkUsed(code, user.userId)
                        analyticsManager.logGroupJoined(code, user.userId)
                    }
                },
                onFailure = { error ->
                    _uiState.value = CreateOrJoinUiState(
                        error = error.message ?: "Failed to join group"
                    )
                }
            )
        }
    }

    fun clearJoinedGroupId() {
        _uiState.value = _uiState.value.copy(joinedGroupId = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
