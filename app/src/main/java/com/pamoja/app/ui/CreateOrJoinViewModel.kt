package com.pamoja.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.util.InviteLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingInviteUiState(
    /** Code waiting to be previewed. Home navigates when this appears. */
    val pendingCode: String? = null,
)

/**
 * Picks up an invite captured before the user could act on it.
 *
 * A link opened by someone with no account is stored, survives sign-in and
 * profile setup, and is honoured the moment Home appears, which is the first
 * point at which the invite can actually be resolved.
 *
 * This used to perform the join itself. It now only surfaces the code, because
 * joining belongs behind the preview screen where a person can see the group
 * and agree to it. That also collapsed most of this class: resolution,
 * membership checks, analytics and error mapping all live in one place now
 * rather than being duplicated between here and the deep-link path.
 */
@HiltViewModel
class CreateOrJoinViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingInviteUiState())
    val uiState: StateFlow<PendingInviteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = userPreferences.pendingInviteCode.firstOrNull()
            if (!stored.isNullOrBlank()) {
                _uiState.value = PendingInviteUiState(pendingCode = stored)
            }
        }
    }

    /** Normalises anything a user can paste into the code the preview expects. */
    fun normalise(rawLinkOrCode: String): String =
        InviteLink.parseCode(rawLinkOrCode) ?: rawLinkOrCode.trim()

    /**
     * Consumed once Home has navigated.
     *
     * The stored code is cleared by the preview screen rather than here, so a
     * navigation that never completes does not lose the invite.
     */
    fun clearPendingCode() {
        _uiState.value = PendingInviteUiState(pendingCode = null)
    }
}
