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
    /** True when the account is still anonymous, so nothing can recover it. */
    val isAnonymous: Boolean = true,
    val email: String? = null,
    val phoneNumber: String? = null,
)

/**
 * Result of starting a phone verification. The verification ID has to be held
 * between sending the code and the user typing it in.
 */
data class PhoneVerification(
    val verificationId: String,
    /** Set when Play Integrity auto-retrieved the code and no OTP entry is needed. */
    val autoVerified: Boolean = false,
)

interface AuthRepository {

    // ── Session ─────────────────────────────────────────────────────────────

    suspend fun getCurrentUser(): User?
    suspend fun isUserLoggedIn(): Boolean
    suspend fun signOut(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>

    /** Which providers are attached to the signed-in account. */
    suspend fun getAuthMethods(): AuthMethods

    // ── Anonymous ───────────────────────────────────────────────────────────

    /**
     * Kept so a first-time user can start walking immediately, before deciding
     * how to sign in. Every real method below LINKS to this account rather than
     * replacing it, so nothing they created is orphaned.
     */
    suspend fun signInAnonymously(): Result<User>

    // ── Email ───────────────────────────────────────────────────────────────

    suspend fun signUpWithEmail(email: String, password: String): Result<User>
    suspend fun signInWithEmail(email: String, password: String): Result<User>
    suspend fun sendPasswordReset(email: String): Result<Unit>

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
    ): Result<PhoneVerification>

    suspend fun verifyPhoneCode(verificationId: String, code: String): Result<User>

    // ── Linking ─────────────────────────────────────────────────────────────
    //
    // The important half. These upgrade the CURRENT account in place, keeping
    // the same UID, so groups, memberships and step history all survive. Without
    // them, signing in would create a brand new UID and silently strand
    // everything the user had already built.

    suspend fun linkGoogle(idToken: String): Result<User>
    suspend fun linkEmail(email: String, password: String): Result<User>
    suspend fun linkPhone(verificationId: String, code: String): Result<User>
}
