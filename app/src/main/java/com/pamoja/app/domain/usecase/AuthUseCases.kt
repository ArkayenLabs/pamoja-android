package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.UserRepository
import javax.inject.Inject

class SignUpUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return Result.failure(Exception("Enter your email address"))
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            return Result.failure(Exception("That email address does not look right"))
        }
        if (password.length < 6) {
            return Result.failure(Exception("Password must be at least 6 characters"))
        }
        return authRepository.signUpWithEmail(trimmed, password)
    }
}

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return Result.failure(Exception("Enter your email address"))
        if (password.isBlank()) return Result.failure(Exception("Enter your password"))
        return authRepository.signInWithEmail(trimmed, password)
    }
}

class SendPasswordResetUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return Result.failure(Exception("Enter your email address"))
        return authRepository.sendPasswordReset(trimmed)
    }
}

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        if (idToken.isBlank()) return Result.failure(Exception("Google sign in was cancelled"))
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
            return Result.failure(Exception("Include the country code, for example +91"))
        }
        val digits = trimmed.drop(1)
        if (digits.length !in 8..15 || !digits.all { it.isDigit() }) {
            return Result.failure(Exception("That phone number does not look right"))
        }
        return authRepository.startPhoneVerification(trimmed, activity)
    }
}

class VerifyPhoneCodeUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(verificationId: String, code: String): Result<User> {
        if (code.length != 6 || !code.all { it.isDigit() }) {
            return Result.failure(Exception("Enter the 6 digit code"))
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
) {
    suspend operator fun invoke(): Result<Unit> {
        val userId = authRepository.getCurrentUser()?.userId
            ?: return Result.failure(Exception("No signed in user"))

        // Asked before anything is destroyed. Deleting the data and only then
        // discovering the session is too stale would erase everything and still
        // leave the account standing.
        if (authRepository.requiresRecentLogin()) {
            return Result.failure(AuthRepository.RecentLoginRequired())
        }

        // Data first. The security rules key on request.auth, so once the Auth
        // record is gone these documents can never be reached again by anyone.
        val dataResult = userRepository.deleteAllUserData(userId)
        if (dataResult.isFailure) {
            return dataResult
        }

        return authRepository.deleteAccount()
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
        if (password.isBlank()) return Result.failure(Exception("Enter your password"))
        return authRepository.reauthenticateWithEmail(password)
    }
}