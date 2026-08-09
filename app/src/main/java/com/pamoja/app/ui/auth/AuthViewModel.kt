package com.pamoja.app.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.data.remote.auth.GoogleCredentialClient
import com.pamoja.app.domain.usecase.GetUserUseCase
import com.pamoja.app.domain.usecase.SendPasswordResetUseCase
import com.pamoja.app.domain.usecase.SignInUseCase
import com.pamoja.app.domain.usecase.SignInWithGoogleUseCase
import com.pamoja.app.domain.usecase.SignUpUseCase
import com.pamoja.app.domain.usecase.StartPhoneVerificationUseCase
import com.pamoja.app.domain.usecase.VerifyPhoneCodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which sign-in button is mid-flight. Only one can be, and only it shows progress. */
enum class AuthMethod { Google, Phone, Email }

/** Where the user goes once authentication succeeds. */
enum class AuthDestination {
    /** No profile in Firestore yet, so this is a new account. */
    ProfileSetup,

    /** Profile already exists, for example signing in on a replacement phone. */
    Home,
}

/** OTP failures need different affordances, so they are modelled rather than stringly typed. */
enum class OtpFailure { WrongCode, Expired, RateLimited }

data class AuthUiState(
    /** Non-null while a method is running. Drives per-button progress. */
    val busyWith: AuthMethod? = null,
    val error: String? = null,
    val destination: AuthDestination? = null,

    // ── Phone ───────────────────────────────────────────────────────────────
    val country: Country = Country.India,
    val phoneNumber: String = "",
    val verificationId: String? = null,
    /**
     * One-shot "a code just went out". Separate from [verificationId], which
     * outlives the navigation because the OTP screen needs it to verify. Keying
     * navigation off verificationId instead would bounce the user straight back
     * into the OTP screen every time they backed out of it.
     */
    val codeSent: Boolean = false,
    val resendSecondsLeft: Int = 0,
    val otpFailure: OtpFailure? = null,

    // ── Email ───────────────────────────────────────────────────────────────
    val resetEmailSentTo: String? = null,
) {
    /** E.164, which is the only form Firebase accepts. */
    val e164: String get() = country.dialCode + phoneNumber.filter { it.isDigit() }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val startPhoneVerificationUseCase: StartPhoneVerificationUseCase,
    private val verifyPhoneCodeUseCase: VerifyPhoneCodeUseCase,
    private val signUpUseCase: SignUpUseCase,
    private val signInUseCase: SignInUseCase,
    private val sendPasswordResetUseCase: SendPasswordResetUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val googleCredentialClient: GoogleCredentialClient,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var resendTimer: Job? = null

    // ── Google ──────────────────────────────────────────────────────────────

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyWith = AuthMethod.Google, error = null)

            val token = googleCredentialClient.getIdToken(activity).getOrElse { e ->
                // A dismissed sheet is a choice, not a failure. Saying "sign in
                // failed" for it reads as an error the user has to fix.
                _uiState.value = _uiState.value.copy(
                    busyWith = null,
                    error = if (e is GoogleCredentialClient.Cancelled) null else e.message
                        ?: "Could not reach Google. Check your connection and try again."
                )
                return@launch
            }

            signInWithGoogleUseCase(token).fold(
                onSuccess = { onAuthenticated(it.userId) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        error = e.message ?: "Google sign in failed"
                    )
                }
            )
        }
    }

    // ── Phone ───────────────────────────────────────────────────────────────

    fun onCountryChange(country: Country) {
        _uiState.value = _uiState.value.copy(country = country, error = null)
    }

    fun onPhoneNumberChange(number: String) {
        _uiState.value = _uiState.value.copy(
            phoneNumber = number.filter { it.isDigit() },
            error = null,
        )
    }

    fun sendCode(activity: Activity) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(busyWith = AuthMethod.Phone, error = null, otpFailure = null)

            startPhoneVerificationUseCase(state.e164, activity).fold(
                onSuccess = { verification ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        verificationId = verification.verificationId,
                        codeSent = true,
                    )
                    startResendCountdown()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        error = friendlyPhoneError(e),
                    )
                }
            )
        }
    }

    fun verifyCode(code: String) {
        val verificationId = _uiState.value.verificationId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                busyWith = AuthMethod.Phone,
                error = null,
                otpFailure = null,
            )

            verifyPhoneCodeUseCase(verificationId, code).fold(
                onSuccess = { onAuthenticated(it.userId) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        otpFailure = classifyOtpFailure(e),
                        error = if (classifyOtpFailure(e) == null) e.message else null,
                    )
                }
            )
        }
    }

    /** Consumed by the phone screen once it has navigated to the OTP screen. */
    fun consumeCodeSent() {
        _uiState.value = _uiState.value.copy(codeSent = false)
    }

    /** Clears the phone leg so the user can go back and correct the number. */
    fun restartPhoneEntry() {
        resendTimer?.cancel()
        _uiState.value = _uiState.value.copy(
            verificationId = null,
            codeSent = false,
            otpFailure = null,
            resendSecondsLeft = 0,
            error = null,
        )
    }

    private fun startResendCountdown() {
        resendTimer?.cancel()
        resendTimer = viewModelScope.launch {
            // Long enough that a slow SMS is not mistaken for a failure, which is
            // what turns one paid message into three.
            for (second in 60 downTo 0) {
                _uiState.value = _uiState.value.copy(resendSecondsLeft = second)
                delay(1_000)
            }
        }
    }

    // ── Email ───────────────────────────────────────────────────────────────

    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyWith = AuthMethod.Email, error = null)
            signUpUseCase(email, password).fold(
                onSuccess = { onAuthenticated(it.userId) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        error = friendlyEmailError(e),
                    )
                }
            )
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyWith = AuthMethod.Email, error = null)
            signInUseCase(email, password).fold(
                onSuccess = { onAuthenticated(it.userId) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        error = friendlyEmailError(e),
                    )
                }
            )
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyWith = AuthMethod.Email, error = null)
            sendPasswordResetUseCase(email).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        resetEmailSentTo = email.trim(),
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        error = friendlyEmailError(e),
                    )
                }
            )
        }
    }

    // ── Shared ──────────────────────────────────────────────────────────────

    /**
     * Decides where an authenticated user lands. A Firestore profile already
     * existing means this is a returning account, for example on a replacement
     * phone, and sending them back through profile setup would be nonsense.
     */
    private suspend fun onAuthenticated(userId: String) {
        userPreferences.saveUserId(userId)

        val existing = getUserUseCase(userId).getOrNull()
        val destination = if (existing != null && existing.name.isNotBlank()) {
            userPreferences.saveUserName(existing.name)
            userPreferences.setOnboarded(true)
            AuthDestination.Home
        } else {
            AuthDestination.ProfileSetup
        }

        _uiState.value = _uiState.value.copy(busyWith = null, destination = destination)
    }

    fun clearDestination() {
        _uiState.value = _uiState.value.copy(destination = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, otpFailure = null)
    }

    fun clearResetConfirmation() {
        _uiState.value = _uiState.value.copy(resetEmailSentTo = null)
    }

    override fun onCleared() {
        resendTimer?.cancel()
        super.onCleared()
    }
}

// ─── Firebase error translation ──────────────────────────────────────────────
// Firebase messages name internal codes and SDK classes. These map the ones a
// user can actually act on; anything unrecognised falls back to plain language
// rather than leaking the original.

private fun friendlyPhoneError(e: Throwable): String {
    val message = e.message.orEmpty()
    return when {
        message.contains("blocked all requests", ignoreCase = true) ||
            message.contains("too many requests", ignoreCase = true) ->
            "Too many attempts from this device. Wait a few minutes and try again."

        message.contains("invalid format", ignoreCase = true) ||
            message.contains("INVALID_PHONE_NUMBER", ignoreCase = true) ->
            "That number does not look right. Check the digits after the dial code."

        message.contains("quota", ignoreCase = true) ->
            "We cannot send codes right now. Try email instead."

        message.contains("network", ignoreCase = true) ->
            "No connection. Check your network and try again."

        else -> "Could not send the code. Try again, or use another method."
    }
}

private fun classifyOtpFailure(e: Throwable): OtpFailure? {
    val message = e.message.orEmpty()
    return when {
        message.contains("expired", ignoreCase = true) -> OtpFailure.Expired
        message.contains("too many", ignoreCase = true) ||
            message.contains("blocked", ignoreCase = true) -> OtpFailure.RateLimited
        message.contains("invalid", ignoreCase = true) ||
            message.contains("6 digit", ignoreCase = true) -> OtpFailure.WrongCode
        else -> null
    }
}

private fun friendlyEmailError(e: Throwable): String {
    val message = e.message.orEmpty()
    return when {
        message.contains("email address is already in use", ignoreCase = true) ->
            "An account already exists with this email. Sign in instead."

        message.contains("password is invalid", ignoreCase = true) ||
            message.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
            message.contains("credential is incorrect", ignoreCase = true) ->
            "That email and password do not match."

        message.contains("no user record", ignoreCase = true) ->
            "No account with this email. Create one instead."

        message.contains("badly formatted", ignoreCase = true) ->
            "That email address does not look right."

        message.contains("network", ignoreCase = true) ->
            "No connection. Check your network and try again."

        // Validation failures raised by the use cases are already user-facing.
        else -> message.ifBlank { "Something went wrong. Try again." }
    }
}
