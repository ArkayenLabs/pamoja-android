package com.pamoja.app.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.data.remote.auth.GoogleCredentialClient
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.PhoneVerification
import com.pamoja.app.domain.repository.PhoneVerificationPurpose
import com.pamoja.app.domain.usecase.CreateUserUseCase
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
    val error: AppError? = null,
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
    private val createUserUseCase: CreateUserUseCase,
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
                    error = if (e is GoogleCredentialClient.Cancelled) null else e.toAppError(),
                )
                return@launch
            }

            signInWithGoogleUseCase(token).fold(
                onSuccess = { onAuthenticated(it) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        error = e.toAppError(),
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
        val state = _uiState.value
        // Capped to the country's own longest valid length, so typing past it
        // is simply impossible rather than producing a "too short" message
        // for a number that is actually too long. The field still accepts a
        // shorter, genuinely incomplete number; only the ceiling is enforced
        // here.
        val digitsOnly = number.filter { it.isDigit() }.take(state.country.nationalDigits.last)
        _uiState.value = state.copy(
            phoneNumber = digitsOnly,
            error = null,
        )
    }

    fun sendCode(activity: Activity) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(busyWith = AuthMethod.Phone, error = null, otpFailure = null)

            startPhoneVerificationUseCase(
                state.e164,
                activity,
                PhoneVerificationPurpose.SignIn,
            ).fold(
                onSuccess = { verification ->
                    when (verification) {
                        is PhoneVerification.CodeSent -> {
                            _uiState.value = _uiState.value.copy(
                                busyWith = null,
                                verificationId = verification.verificationId,
                                codeSent = true,
                            )
                            startResendCountdown()
                        }

                        is PhoneVerification.Completed -> {
                            val user = verification.user
                            if (user == null) {
                                _uiState.value = _uiState.value.copy(
                                    busyWith = null,
                                    error = AppError.Unknown(
                                        "Automatic phone sign in returned no user"
                                    ),
                                )
                            } else {
                                onAuthenticated(user)
                            }
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        error = e.toAppError(),
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
                onSuccess = { onAuthenticated(it) },
                onFailure = { e ->
                    val appError = e.toAppError()
                    val failure = appError.toOtpFailure()
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        otpFailure = failure,
                        // Only when the failure is not one of the three the OTP
                        // screen draws itself, so the same thing is never said
                        // in two places.
                        error = appError.takeIf { failure == null },
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

    fun signUpWithEmail(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyWith = AuthMethod.Email, error = null)
            signUpUseCase(email, password).fold(
                // Email/password authentication has no provider profile. Carry
                // the name from the same account-creation form so a successful
                // sign-up does not lead to a second, disconnected form.
                onSuccess = { onAuthenticated(it.copy(name = name.trim())) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyWith = AuthMethod.Email, error = null)
            signInUseCase(email, password).fold(
                onSuccess = { onAuthenticated(it) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        busyWith = null,
                        error = e.toAppError(),
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
                        error = e.toAppError(),
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
    private suspend fun onAuthenticated(authenticatedUser: User) {
        val userId = authenticatedUser.userId
        userPreferences.saveUserId(userId)

        val existingResult = getUserUseCase(userId)
        val existing = existingResult.getOrNull()
        if (existing != null && existing.name.isNotBlank()) {
            userPreferences.saveUserName(existing.name)
            userPreferences.setOnboarded(true)
            _uiState.value = _uiState.value.copy(
                busyWith = null,
                destination = AuthDestination.Home,
            )
            return
        }

        // A network or rules failure is not evidence that the profile is
        // missing. Treating every failed read as a new user could overwrite a
        // returning person's profile with provider data.
        val readError = existingResult.exceptionOrNull()?.toAppError()
        if (readError != null && readError !is AppError.NotFound) {
            _uiState.value = _uiState.value.copy(busyWith = null, error = readError)
            return
        }

        // Google provides a verified display name. Email sign-up carries the
        // name collected on the same form. Phone provides neither, so only that
        // path (and the rare provider account with no name) needs the small
        // profile screen.
        val proposedName = authenticatedUser.name.trim().take(50).trim()
        if (proposedName.isBlank()) {
            _uiState.value = _uiState.value.copy(
                busyWith = null,
                destination = AuthDestination.ProfileSetup,
            )
            return
        }

        val profile = authenticatedUser.copy(name = proposedName)
        // Keep the provider/form name locally before the remote write. If the
        // write fails after Firebase has already created the account, the user
        // can finish from ProfileSetup instead of retrying sign-up and seeing
        // an "account already exists" error.
        userPreferences.saveUserName(proposedName)
        createUserUseCase(profile).fold(
            onSuccess = {
                userPreferences.setOnboarded(true)
                _uiState.value = _uiState.value.copy(
                    busyWith = null,
                    destination = AuthDestination.Home,
                )
            },
            onFailure = {
                _uiState.value = _uiState.value.copy(
                    busyWith = null,
                    destination = AuthDestination.ProfileSetup,
                )
            },
        )
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

/**
 * Which of the OTP screen's three drawn states this failure is, if any.
 *
 * Replaces a set of functions that searched Firebase's English message text for
 * substrings like "expired". That worked only in English and only until Google
 * reworded anything, and it put user-facing copy in the ViewModel where it
 * could not be translated. Classification now happens once in the data layer
 * and this only reads the resulting type.
 */
private fun AppError.toOtpFailure(): OtpFailure? = when (this) {
    is AppError.Expired -> OtpFailure.Expired
    is AppError.RateLimited -> OtpFailure.RateLimited
    is AppError.InvalidCredentials -> OtpFailure.WrongCode
    // The use case rejects anything that is not six digits before it reaches
    // the network, and that is a wrong code as far as the user is concerned.
    is AppError.Validation -> OtpFailure.WrongCode
    else -> null
}
