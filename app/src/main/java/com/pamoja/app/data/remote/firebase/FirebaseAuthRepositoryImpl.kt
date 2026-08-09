package com.pamoja.app.data.remote.firebase

import android.app.Activity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.AuthMethods
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.PhoneVerification
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Firebase implementation of authentication.
 *
 * Sign-in is a hard gate: there are no anonymous sessions, so a user always
 * arrives here with one of Google, phone or email before any group or step data
 * exists. That is why these methods sign in directly rather than linking. The
 * link* methods at the bottom are for a different case entirely, adding a second
 * method to an account that is already signed in.
 */
class FirebaseAuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth
) : AuthRepository {

    // ── Session ─────────────────────────────────────────────────────────────

    override suspend fun getCurrentUser(): User? =
        auth.currentUser?.let { User(userId = it.uid) }

    override suspend fun isUserLoggedIn(): Boolean = auth.currentUser != null

    override suspend fun signOut(): Result<Unit> = runCatching { auth.signOut() }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("No signed in user")
        user.delete().await()
    }

    override suspend fun getAuthMethods(): AuthMethods {
        val user = auth.currentUser ?: return AuthMethods()
        val providers = user.providerData.map { it.providerId }
        return AuthMethods(
            hasGoogle = providers.contains(GoogleAuthProvider.PROVIDER_ID),
            hasPhone = providers.contains(PhoneAuthProvider.PROVIDER_ID),
            hasEmail = providers.contains(EmailAuthProvider.PROVIDER_ID),
            email = user.email,
            phoneNumber = user.phoneNumber,
        )
    }

    // ── Email ───────────────────────────────────────────────────────────────

    override suspend fun signUpWithEmail(email: String, password: String): Result<User> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            User(userId = result.user?.uid ?: error("Sign up failed"))
        }

    override suspend fun signInWithEmail(email: String, password: String): Result<User> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            User(userId = result.user?.uid ?: error("Sign in failed"))
        }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
    }

    // ── Google ──────────────────────────────────────────────────────────────

    override suspend fun signInWithGoogle(idToken: String): Result<User> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        User(userId = result.user?.uid ?: error("Google sign in failed"))
    }

    // ── Phone ───────────────────────────────────────────────────────────────

    override suspend fun startPhoneVerification(
        phoneNumber: String,
        activity: Any,
    ): Result<PhoneVerification> = runCatching {
        val hostActivity = activity as? Activity
            ?: error("Phone verification needs an Activity")

        suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Play Integrity auto-retrieved the SMS. No OTP screen needed.
                    if (cont.isActive) {
                        cont.resume(
                            PhoneVerification(
                                verificationId = credential.smsCode.orEmpty(),
                                autoVerified = true,
                            )
                        )
                    }
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    if (cont.isActive) cont.cancel(e)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    if (cont.isActive) {
                        cont.resume(PhoneVerification(verificationId = verificationId))
                    }
                }
            }

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(hostActivity)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    override suspend fun verifyPhoneCode(verificationId: String, code: String): Result<User> =
        runCatching {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val result = auth.signInWithCredential(credential).await()
            User(userId = result.user?.uid ?: error("Phone sign in failed"))
        }

    // ── Explicit linking, from Settings ─────────────────────────────────────
    // Used when an already signed-in user adds a second method.

    override suspend fun linkGoogle(idToken: String): Result<User> = runCatching {
        val user = auth.currentUser ?: error("No signed in user")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = user.linkWithCredential(credential).await()
        User(userId = result.user?.uid ?: error("Link failed"))
    }

    override suspend fun linkEmail(email: String, password: String): Result<User> = runCatching {
        val user = auth.currentUser ?: error("No signed in user")
        val credential = EmailAuthProvider.getCredential(email, password)
        val result = user.linkWithCredential(credential).await()
        User(userId = result.user?.uid ?: error("Link failed"))
    }

    override suspend fun linkPhone(verificationId: String, code: String): Result<User> =
        runCatching {
            val user = auth.currentUser ?: error("No signed in user")
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val result = user.linkWithCredential(credential).await()
            User(userId = result.user?.uid ?: error("Link failed"))
        }
}
