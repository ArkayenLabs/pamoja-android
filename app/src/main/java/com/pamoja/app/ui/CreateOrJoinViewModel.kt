package com.pamoja.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.util.InviteLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingInviteUiState(
    /** Code waiting for the person to explicitly resume or dismiss. */
    val pendingCode: String? = null,
)

internal object PendingInvitePolicy {
    const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L

    fun isFresh(savedAt: Long, now: Long): Boolean =
        savedAt > 0L && now >= savedAt && now - savedAt <= MAX_AGE_MILLIS
}

/**
 * Picks up an invite captured before the user could act on it.
 *
 * A link opened by someone with no account is stored across sign-in and profile
 * setup. Home then offers it as an explicit continuation instead of navigating
 * without context during a later, unrelated app session.
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
            val stored = userPreferences.pendingInvite.first()
            when {
                stored == null -> Unit
                PendingInvitePolicy.isFresh(stored.savedAt, System.currentTimeMillis()) -> {
                    _uiState.value = PendingInviteUiState(pendingCode = stored.code)
                }
                else -> userPreferences.clearPendingInviteCode()
            }
        }
    }

    /**
     * Normalises anything a user can paste into the code the preview expects.
     *
     * The fallback is the dangerous half. parseCode returns null for anything
     * that is not a Pamoja link, and returning the raw text regardless meant
     * that pasting any URL, or scanning any QR code that carries one, produced
     * a "code" full of slashes. Home passes that to onOpenInvite, which splices
     * it into the single-segment route `join/{code}`, and navigation throws.
     * Scanning someone else's wifi or website QR should say "not a Pamoja
     * link", not take the app down.
     *
     * Blank is the right answer for unusable input: Home already treats a blank
     * code as "not a Pamoja link" and shows that message.
     */
    fun normalise(rawLinkOrCode: String): String {
        InviteLink.parseCode(rawLinkOrCode)?.let { return it }
        val bare = rawLinkOrCode.trim()
        // A bare invite code, hand-typed. Anything with a slash or whitespace is
        // a pasted link that parseCode has already refused, not a code.
        return if (bare.none { it == '/' || it.isWhitespace() }) bare else ""
    }

    /**
     * Consumed once Home has navigated.
     *
     * The stored code is cleared by the preview screen rather than here, so a
     * navigation that never completes does not lose the invite.
     */
    fun markPendingInviteOpened() {
        _uiState.value = PendingInviteUiState(pendingCode = null)
    }

    fun dismissPendingInvite() {
        _uiState.value = PendingInviteUiState(pendingCode = null)
        viewModelScope.launch { userPreferences.clearPendingInviteCode() }
    }
}
