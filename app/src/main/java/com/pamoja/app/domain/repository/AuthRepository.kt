package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.User

/**
 * Which sign-in methods an account currently has attached.
 * Drives the account section of the profile screen.
 */
data class AuthMethods(
    val hasGoogle: Boolean = false,
    val hasPhone: Boolean = false,
    val hasEmail: Boolean = false,
    val email: String? = null,
    val phoneNumber: String? = null,
)

/** What Firebase should do if it verifies the phone before manual OTP entry. */
enum class PhoneVerificationPurpose { SignIn, Link, Reauthenticate }

/** Result of starting a phone verification. */
sealed interface PhoneVerification {
    /** Hold this ID until the user enters the code from the SMS. */
    data class CodeSent(val verificationId: String) : PhoneVerification

    /**
     * Firebase verified the phone immediately and completed [purpose] without
     * manual OTP entry. [user] is present for sign-in and linking; a successful
     * reauthentication keeps the already signed-in user.
     */
    data class Completed(val user: User? = null) : PhoneVerification
}

interface AuthRepository {

    /**
     * Firebase refuses destructive operations on a session that has not signed
     * in recently, roughly within the last five minutes. Since sessions now last
     * indefinitely, this is the normal case for account deletion rather than an
     * edge case, so it is modelled instead of being surfaced as a raw failure.
     */
    class RecentLoginRequired : Exception("Recent login required for destructive operation")

    // ── Session ─────────────────────────────────────────────────────────────

    suspend fun getCurrentUser(): User?
    suspend fun isUserLoggedIn(): Boolean
    suspend fun signOut(): Result<Unit>

    /**
     * Deletes the Firebase Auth account only. Firestore data must already be
     * gone, see [UserRepository.deleteAllUserData].
     *
     * Fails with [RecentLoginRequired] when the session is too old, in which
     * case the caller must re-authenticate and try again.
     */
    suspend fun deleteAccount(): Result<Unit>

    // ── Re-authentication, for destructive operations ───────────────────────

    /**
     * True when the session is too old for Firebase to accept a destructive
     * operation. Checked before deleting anything, so a stale session is caught
     * while the data is still intact.
     */
    suspend fun requiresRecentLogin(): Boolean

    suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit>
    suspend fun reauthenticateWithEmail(password: String): Result<Unit>
    suspend fun reauthenticateWithPhone(verificationId: String, code: String): Result<Unit>

    /** Which providers are attached to the signed-in account. */
    suspend fun getAuthMethods(): AuthMethods

    // ── Email ───────────────────────────────────────────────────────────────

    suspend fun signUpWithEmail(email: String, password: String): Result<User>
    suspend fun signInWithEmail(email: String, password: String): Result<User>
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Replaces the password on the signed-in account.
     *
     * Firebase refuses this on a session that has not signed in recently, which
     * for this app is every session. Callers must re-authenticate immediately
     * before calling it, which is what `ChangePasswordUseCase` does.
     */
    suspend fun updatePassword(newPassword: String): Result<Unit>

    // ── Google ──────────────────────────────────────────────────────────────

    /**
     * Completes Google sign-in with an ID token obtained from Credential Manager.
     * The UI layer owns fetching that token, since it needs an Activity context.
     */
    suspend fun signInWithGoogle(idToken: String): Result<User>

    // ── Phone ───────────────────────────────────────────────────────────────

    /**
     * Sends an SMS code. [phoneNumber] must be in E.164 form, for example
     * +919876543210.
     *
     * Each send costs real money, roughly $0.01 per verification in India, and
     * requires the Blaze plan. Callers must rate limit and never retry blindly.
     */
    suspend fun startPhoneVerification(
        phoneNumber: String,
        activity: Any,
        purpose: PhoneVerificationPurpose,
    ): Result<PhoneVerification>

    suspend fun verifyPhoneCode(verificationId: String, code: String): Result<User>

    // ── Linking ─────────────────────────────────────────────────────────────
    //
    // Attaching a SECOND method to an account that is already signed in, from
    // the account section of Settings. These keep the same UID, so groups,
    // memberships and step history all survive.

    suspend fun linkGoogle(idToken: String): Result<User>
    suspend fun linkEmail(email: String, password: String): Result<User>
    suspend fun linkPhone(verificationId: String, code: String): Result<User>
}
