package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.AvatarRepository
import com.pamoja.app.domain.repository.UserRepository
import javax.inject.Inject

class SignUpUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return Result.failure(AppError.Validation(ValidationField.EmailMissing))
        if (!trimmed.looksLikeEmail()) {
            return Result.failure(AppError.Validation(ValidationField.EmailMalformed))
        }
        if (password.length < 6) {
            return Result.failure(AppError.Validation(ValidationField.PasswordTooShort))
        }
        return authRepository.signUpWithEmail(trimmed, password)
    }
}

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return Result.failure(AppError.Validation(ValidationField.EmailMissing))
        if (password.isBlank()) return Result.failure(AppError.Validation(ValidationField.PasswordMissing))
        return authRepository.signInWithEmail(trimmed, password)
    }
}

class SendPasswordResetUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return Result.failure(AppError.Validation(ValidationField.EmailMissing))
        return authRepository.sendPasswordReset(trimmed)
    }
}

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        if (idToken.isBlank()) return Result.failure(AppError.Unknown("Google returned a blank token"))
        return authRepository.signInWithGoogle(idToken)
    }
}

/**
 * Sends an SMS code.
 *
 * Every call costs money, roughly $0.01 per verification in India, so the number
 * is validated here rather than letting a typo burn a message. E.164 format is
 * required by Firebase, for example +919876543210.
 */
class StartPhoneVerificationUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        phoneNumber: String,
        activity: Any,
    ): Result<com.pamoja.app.domain.repository.PhoneVerification> {
        val trimmed = phoneNumber.replace(" ", "").replace("-", "")
        if (!trimmed.startsWith("+")) {
            return Result.failure(AppError.Validation(ValidationField.PhoneMissingCountryCode))
        }
        val digits = trimmed.drop(1)
        if (digits.length !in 8..15 || !digits.all { it.isDigit() }) {
            return Result.failure(AppError.Validation(ValidationField.PhoneMalformed))
        }
        return authRepository.startPhoneVerification(trimmed, activity)
    }
}

class VerifyPhoneCodeUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(verificationId: String, code: String): Result<User> {
        if (code.length != 6 || !code.all { it.isDigit() }) {
            return Result.failure(AppError.Validation(ValidationField.OtpIncomplete))
        }
        return authRepository.verifyPhoneCode(verificationId, code)
    }
}

class GetAuthMethodsUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.getAuthMethods()
}

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return authRepository.signOut()
    }
}

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): User? {
        return authRepository.getCurrentUser()
    }
}

class IsUserLoggedInUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Boolean {
        return authRepository.isUserLoggedIn()
    }
}

/**
 * Erases the account completely: Firestore data first, then the Auth record.
 *
 * Deleting only the Auth record, which is what this used to do, leaves the user
 * document, every membership and every step entry behind with no account able
 * to reach them. That is a right-to-erasure failure, and the orphaned
 * memberships also leave every group they belonged to permanently over-counted.
 *
 * Fails with [AuthRepository.RecentLoginRequired] when the session is too old.
 * Nothing is deleted in that case, so the caller can re-authenticate and call
 * again safely.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val avatarRepository: AvatarRepository,
) {
    /**
     * [justReauthenticated] skips the staleness pre-check on the retry that
     * follows a successful re-authentication.
     *
     * The pre-check reads lastSignInTimestamp, and Firebase does not document
     * whether reauthenticate() refreshes it. If it does not, the check would
     * keep reporting a stale session and the confirmation dialog would reappear
     * forever. Skipping it costs nothing: deleteAccount still raises
     * RecentLoginRequired if the server disagrees, which lands the user back at
     * the dialog exactly once rather than in a loop.
     */
    suspend operator fun invoke(justReauthenticated: Boolean = false): Result<Unit> {
        val userId = authRepository.getCurrentUser()?.userId
            ?: return Result.failure(AppError.SessionExpired())

        // Asked before anything is destroyed. Deleting the data and only then
        // discovering the session is too stale would erase everything and still
        // leave the account standing.
        if (!justReauthenticated && authRepository.requiresRecentLogin()) {
            return Result.failure(AuthRepository.RecentLoginRequired())
        }

        // Data first. The security rules key on request.auth, so once the Auth
        // record is gone these documents can never be reached again by anyone.
        // The photo lives in Storage, not Firestore, so deleting the documents
        // would leave it behind: a face, still hosted, after the account that
        // owned it is gone. Deliberately before the Firestore wipe and
        // deliberately not checked, since it succeeds trivially when the user
        // never set one and must never block the erasure of everything else.
        avatarRepository.deleteAvatar(userId)

        val dataResult = userRepository.deleteAllUserData(userId)
        if (dataResult.isFailure) {
            return dataResult
        }

        return authRepository.deleteAccount()
    }
}

// ── Adding a second sign-in method ──────────────────────────────────────────
//
// Linking rather than signing in: the UID is kept, so groups, memberships and
// step history all survive. An account with one method is one lost phone or one
// forgotten password away from losing all of it, which is what these are for.
//
// The interesting failure is a collision: the credential already belongs to a
// different Pamoja account. Firebase reports it as
// FirebaseAuthUserCollisionException and the data layer maps it to
// AppError.Conflict, so callers match on the type rather than the wording.

class LinkGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        if (idToken.isBlank()) return Result.failure(AppError.Unknown("Google returned a blank token"))
        return authRepository.linkGoogle(idToken)
    }
}

class LinkEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return Result.failure(AppError.Validation(ValidationField.EmailMissing))
        if (!trimmed.looksLikeEmail()) {
            return Result.failure(AppError.Validation(ValidationField.EmailMalformed))
        }
        // Same floor as signing up, so a password that could not have created an
        // account cannot be attached to one either.
        if (password.length < 6) {
            return Result.failure(AppError.Validation(ValidationField.PasswordTooShort))
        }
        return authRepository.linkEmail(trimmed, password)
    }
}

class LinkPhoneUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(verificationId: String, code: String): Result<User> {
        if (code.length != 6 || !code.all { it.isDigit() }) {
            return Result.failure(AppError.Validation(ValidationField.OtpIncomplete))
        }
        return authRepository.linkPhone(verificationId, code)
    }
}

/**
 * Changes the account password, proving ownership first.
 *
 * Two repository calls in a fixed order, for the same reason
 * [DeleteAccountUseCase] sequences two: Firebase rejects a password change on a
 * stale session, and sessions here last indefinitely, so re-authenticating is
 * the normal path rather than a recovery path.
 *
 * Asking for the current password is not only Firebase's requirement. It is what
 * stops an unlocked phone left on a table from becoming a permanent account
 * takeover.
 */
class ChangePasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(currentPassword: String, newPassword: String): Result<Unit> {
        if (currentPassword.isBlank()) {
            return Result.failure(AppError.Validation(ValidationField.PasswordMissing))
        }
        if (newPassword.length < 6) {
            return Result.failure(AppError.Validation(ValidationField.PasswordTooShort))
        }

        // Nothing has changed yet if this fails, so a wrong current password
        // leaves the account exactly as it was.
        val reauthenticated = authRepository.reauthenticateWithEmail(currentPassword)
        if (reauthenticated.isFailure) return reauthenticated

        return authRepository.updatePassword(newPassword)
    }
}

class ReauthenticateWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<Unit> =
        authRepository.reauthenticateWithGoogle(idToken)
}

class ReauthenticateWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(password: String): Result<Unit> {
        if (password.isBlank()) return Result.failure(AppError.Validation(ValidationField.PasswordMissing))
        return authRepository.reauthenticateWithEmail(password)
    }
}

class ReauthenticateWithPhoneUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(verificationId: String, code: String): Result<Unit> {
        if (code.length != 6 || !code.all { it.isDigit() }) {
            return Result.failure(AppError.Validation(ValidationField.OtpIncomplete))
        }
        return authRepository.reauthenticateWithPhone(verificationId, code)
    }
}
/**
 * A deliberately loose email check.
 *
 * Replaces android.util.Patterns.EMAIL_ADDRESS, which was the one Android
 * dependency in the domain layer and broke the rule that this layer stays pure
 * Kotlin. It was also untestable without an instrumented test, since Patterns
 * returns null on the JVM.
 *
 * Loose on purpose. The only authority on whether an address exists is the mail
 * server, so this catches obvious typos and lets the server judge the rest.
 * Strict client-side email regexes are famous for rejecting valid addresses.
 */
private val EMAIL_SHAPE = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]{2,}$""")

private fun String.looksLikeEmail(): Boolean = EMAIL_SHAPE.matches(this)
