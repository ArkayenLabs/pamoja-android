package com.pamoja.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.analytics.AnalyticsEvent
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.analytics.AnalyticsParam
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Signal
import com.pamoja.app.domain.model.SignalKind
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.ObserveReceivedSignalsUseCase
import com.pamoja.app.domain.usecase.ObserveSentSignalsUseCase
import com.pamoja.app.domain.usecase.SendSignalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SignalsUiState(
    val groupId: String = "",
    val sent: List<Signal> = emptyList(),
    val received: List<Signal> = emptyList(),
    /** Recipients of a send still on its way, so a heart fills on the tap itself. */
    val sending: Set<Pair<SignalKind, String>> = emptySet(),
    /** A send failed. Snackbar only; the heart simply stays empty. */
    val error: AppError? = null,
    /**
     * Explain notifications now: the moment they obviously matter, having just
     * cheered or nudged someone or looking at a cheer. Asked once, ever.
     */
    val showNotificationPrimer: Boolean = false,
) {
    fun hasSent(kind: SignalKind, toUserId: String): Boolean =
        (kind to toUserId) in sending || sent.any { it.kind == kind && it.toUserId == toUserId }

    /** Cheers to this person today, most recent sender first as they arrived. */
    val cheersReceived: List<Signal> get() = received.filter { it.kind == SignalKind.CHEER }
}

/**
 * Cheers and nudges for the group Home is showing, for today.
 *
 * Its own ViewModel rather than more of [com.pamoja.app.ui.group.GroupViewModel],
 * whose single members listener is deliberately the only read it makes.
 * Both listeners here answer from the local cache first like everything else
 * on Home, and a failure to read them only hides the social parts: the
 * group's figures never depend on them.
 */
@HiltViewModel
class GroupSignalsViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val observeSentSignalsUseCase: ObserveSentSignalsUseCase,
    private val observeReceivedSignalsUseCase: ObserveReceivedSignalsUseCase,
    private val sendSignalUseCase: SendSignalUseCase,
    private val userPreferences: UserPreferences,
    private val analyticsManager: AnalyticsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignalsUiState())
    val uiState: StateFlow<SignalsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var userId: String? = null
    private var loadedFor: Pair<String, String>? = null

    /**
     * Starts listening for [groupId]'s signals today. Called again on resume,
     * which is a no-op unless the date has moved on since.
     */
    fun load(groupId: String, today: LocalDate = LocalDate.now()) {
        val date = today.toString()
        if (loadedFor == groupId to date) return
        loadedFor = groupId to date
        loadJob?.cancel()
        _uiState.value = SignalsUiState(groupId = groupId, showNotificationPrimer = _uiState.value.showNotificationPrimer)
        loadJob = viewModelScope.launch {
            val uid = userId ?: getCurrentUserUseCase()?.userId?.also { userId = it } ?: return@launch
            launch {
                observeSentSignalsUseCase(groupId, uid, date)
                    .catch { emit(emptyList()) }
                    .collect { sent ->
                        _uiState.value = _uiState.value.copy(
                            sent = sent,
                            sending = _uiState.value.sending.filterNot { (kind, to) ->
                                sent.any { it.kind == kind && it.toUserId == to }
                            }.toSet(),
                        )
                    }
            }
            launch {
                observeReceivedSignalsUseCase(groupId, uid, date)
                    .catch { emit(emptyList()) }
                    .collect { received ->
                        _uiState.value = _uiState.value.copy(received = received)
                        if (received.any { it.kind == SignalKind.CHEER }) offerNotifications()
                    }
            }
        }
    }

    fun cheer(toUserIds: List<String>) = send(SignalKind.CHEER, toUserIds)

    fun nudge(toUserIds: List<String>) = send(SignalKind.NUDGE, toUserIds)

    private fun send(kind: SignalKind, toUserIds: List<String>) {
        val state = _uiState.value
        val (groupId, date) = loadedFor ?: return
        val uid = userId ?: return
        val fresh = toUserIds.filter { it != uid && !state.hasSent(kind, it) }.distinct()
        if (fresh.isEmpty()) return
        _uiState.value = state.copy(sending = state.sending + fresh.map { kind to it })

        viewModelScope.launch {
            sendSignalUseCase(groupId, kind, uid, fresh, date, alreadySent = _uiState.value.sent).fold(
                onSuccess = {
                    analyticsManager.logEvent(
                        if (kind == SignalKind.CHEER) AnalyticsEvent.CHEER_SENT else AnalyticsEvent.NUDGE_SENT,
                        mapOf(AnalyticsParam.GROUP_ID to groupId, AnalyticsParam.RECIPIENTS to fresh.size),
                    )
                    offerNotifications()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        sending = _uiState.value.sending - fresh.map { kind to it }.toSet(),
                        error = error.toAppError(),
                    )
                },
            )
        }
    }

    private suspend fun offerNotifications() {
        if (_uiState.value.showNotificationPrimer) return
        if (!userPreferences.notificationPrimerShown.first()) {
            _uiState.value = _uiState.value.copy(showNotificationPrimer = true)
        }
    }

    /**
     * Recorded whichever way it was answered. The point is to ask once:
     * re-asking someone who declined is how an app earns a permanent denial,
     * and on Android 13+ that cannot be undone from inside the app.
     */
    fun onNotificationPrimerAnswered() {
        _uiState.value = _uiState.value.copy(showNotificationPrimer = false)
        viewModelScope.launch { userPreferences.setNotificationPrimerShown() }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
