package com.pamoja.app.ui.account

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.R
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.data.remote.auth.GoogleCredentialClient
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.repository.AuthMethods
import com.pamoja.app.domain.usecase.ChangePasswordUseCase
import com.pamoja.app.domain.usecase.GetAuthMethodsUseCase
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.LinkEmailUseCase
import com.pamoja.app.domain.usecase.LinkGoogleUseCase
import com.pamoja.app.domain.usecase.LinkPhoneUseCase
import com.pamoja.app.domain.usecase.StartPhoneVerificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One thing the user can do on this screen.
 *
 * Doubles as the address for a failure: an error carries the flow it came from,
 * so a rejected Google link is drawn under the Google row and a wrong SMS code
 * inside the dialog that asked for it, rather than both landing in the same
 * anonymous box at the bottom.
 */
enum class AccountFlow { AddGoogle, AddEmail, AddPhone, ChangePassword }

data class AccountUiState(
    val isLoading: Boolean = true,
    /** The failure that stopped the account loading, which is a whole-screen state. */
    val loadError: AppError? = null,
    val isOffline: Boolean = false,

    val methods: AuthMethods = AuthMethods(),

    /** Which dialog is open. Adding Google is one tap and never opens one. */
    val activeDialog: AccountFlow? = null,
    /** Which action is in flight, so only its own control shows progress. */
    val busyWith: AccountFlow? = null,

    val error: AppError? = null,
    /** Which action [error] belongs to, so it is rendered next to that action. */
    val errorFrom: AccountFlow? = null,

    /** Confirmation as a resource id, so no user-facing copy lives here. */
    val messageRes: Int? = null,

    // ── Adding a phone number ───────────────────────────────────────────────
    /** Set once the SMS is away, which is what swaps the dialog to code entry. */
    val verificationId: String? = null,
    /** The number the code went to, shown so a wrong one is spotted before retrying. */
    val pendingPhoneNumber: String? = null,
) {
    /** How many ways back into this account exist today. */
    val methodCount: Int =
        listOf(methods.hasGoogle, methods.hasPhone, methods.hasEmail).count { it }

    /**
     * Linking is a network call, so offline it would hang rather than fail.
     * The controls are disabled and the reason is stated instead.
     */
    val canLink: Boolean get() = !isOffline && busyWith == null
}

/**
 * Which ways back into this account exist, and adding another.
 *
 * `AuthRepository.linkGoogle/linkEmail/linkPhone` were written for exactly this
 * and had no caller until now, so every account created since sign-in became
 * mandatory has had precisely one way in. Losing the phone that holds the SIM,
 * or the Google account, meant losing every group and all step history with no
 * recovery path, because there is no support channel that can prove who you are.
 *
 * Linking keeps the same UID, which is the whole point: nothing about the
 * account changes except how many doors it has.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getAuthMethodsUseCase: GetAuthMethodsUseCase,
    private val linkGoogleUseCase: LinkGoogleUseCase,
    private val linkEmailUseCase: LinkEmailUseCase,
    private val linkPhoneUseCase: LinkPhoneUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val startPhoneVerificationUseCase: StartPhoneVerificationUseCase,
    private val googleCredentialClient: GoogleCredentialClient,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

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

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)

            // getAuthMethods reads providerData locally and cannot fail, so the
            // only load failure worth modelling is having no session at all.
            val current = getCurrentUserUseCase()
            if (current == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = AppError.SessionExpired(),
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                methods = getAuthMethodsUseCase(),
            )
        }
    }

    // ── Google ──────────────────────────────────────────────────────────────

    fun addGoogle(activity: Activity) {
        viewModelScope.launch {
            startWork(AccountFlow.AddGoogle)

            val token = googleCredentialClient.getIdToken(activity).getOrElse { e ->
                // A dismissed Google sheet is a choice, not a failure.
                if (e is GoogleCredentialClient.Cancelled) {
                    _uiState.value = _uiState.value.copy(busyWith = null)
                } else {
                    fail(AccountFlow.AddGoogle, e)
                }
                return@launch
            }

            linkGoogleUseCase(token).fold(
                onSuccess = { succeed(R.string.account_google_added) },
                onFailure = { fail(AccountFlow.AddGoogle, it) },
            )
        }
    }

    // ── Email ───────────────────────────────────────────────────────────────

    fun addEmail(email: String, password: String) {
        viewModelScope.launch {
            startWork(AccountFlow.AddEmail)
            linkEmailUseCase(email, password).fold(
                onSuccess = { succeed(R.string.account_email_added) },
                onFailure = { fail(AccountFlow.AddEmail, it) },
            )
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            startWork(AccountFlow.ChangePassword)
            changePasswordUseCase(currentPassword, newPassword).fold(
                onSuccess = { succeed(R.string.account_password_changed) },
                onFailure = { fail(AccountFlow.ChangePassword, it) },
            )
        }
    }

    // ── Phone ───────────────────────────────────────────────────────────────

    /**
     * Sends the SMS. [e164] is the country's dial code plus the digits typed.
     *
     * Every send costs real money, so the dialog gates this behind a
     * country-appropriate digit count rather than letting a half-typed number
     * burn a message.
     */
    fun sendPhoneCode(e164: String, activity: Activity) {
        viewModelScope.launch {
            startWork(AccountFlow.AddPhone)

            startPhoneVerificationUseCase(e164, activity).fold(
                onSuccess = { verification ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        verificationId = verification.verificationId,
                        pendingPhoneNumber = e164,
                    )
                },
                onFailure = { fail(AccountFlow.AddPhone, it) },
            )
        }
    }

    fun confirmPhoneCode(code: String) {
        val verificationId = _uiState.value.verificationId ?: return
        viewModelScope.launch {
            startWork(AccountFlow.AddPhone)
            linkPhoneUseCase(verificationId, code).fold(
                onSuccess = { succeed(R.string.account_phone_added) },
                onFailure = { fail(AccountFlow.AddPhone, it) },
            )
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────

    fun openDialog(flow: AccountFlow) {
        _uiState.value = _uiState.value.copy(
            activeDialog = flow,
            error = null,
            errorFrom = null,
            verificationId = null,
            pendingPhoneNumber = null,
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            activeDialog = null,
            busyWith = null,
            error = null,
            errorFrom = null,
            verificationId = null,
            pendingPhoneNumber = null,
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null)
    }

    // ── Shared ──────────────────────────────────────────────────────────────

    private fun startWork(flow: AccountFlow) {
        _uiState.value = _uiState.value.copy(
            busyWith = flow,
            error = null,
            errorFrom = null,
        )
    }

    /**
     * Re-reads the methods rather than assuming the link took, so the list
     * always reflects Firebase rather than what this screen believes it did.
     */
    private suspend fun succeed(messageRes: Int) {
        _uiState.value = _uiState.value.copy(
            busyWith = null,
            activeDialog = null,
            verificationId = null,
            pendingPhoneNumber = null,
            methods = getAuthMethodsUseCase(),
            messageRes = messageRes,
        )
    }

    private fun fail(flow: AccountFlow, cause: Throwable) {
        _uiState.value = _uiState.value.copy(
            busyWith = null,
            error = cause.toAppError(),
            errorFrom = flow,
        )
    }
}
