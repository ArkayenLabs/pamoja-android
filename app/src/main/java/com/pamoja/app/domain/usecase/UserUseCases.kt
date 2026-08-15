package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.UserDataExport
import com.pamoja.app.domain.repository.AvatarRepository
import com.pamoja.app.domain.repository.UserRepository
import javax.inject.Inject

class CreateUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(user: User): Result<Unit> {
        if (user.userId.isBlank()) return Result.failure(AppError.SessionExpired())
        if (user.name.isBlank()) return Result.failure(AppError.Validation(ValidationField.DisplayNameMissing))
        return userRepository.createUser(user)
    }
}

class GetUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String): Result<User> {
        if (userId.isBlank()) return Result.failure(AppError.SessionExpired())
        return userRepository.getUser(userId)
    }
}

class UpdateUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(user: User): Result<Unit> {
        if (user.userId.isBlank()) return Result.failure(AppError.SessionExpired())
        if (user.name.isBlank()) return Result.failure(AppError.Validation(ValidationField.DisplayNameMissing))
        return userRepository.updateUser(user)
    }
}

class SaveDeviceTokenUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, token: String): Result<Unit> {
        if (userId.isBlank()) return Result.failure(AppError.SessionExpired())
        if (token.isBlank()) return Result.failure(AppError.Unknown("Blank FCM token"))
        return userRepository.saveDeviceToken(userId, token)
    }
}
/**
 * Everything Pamoja holds about the signed-in user, for export.
 *
 * The counterpart to deleting an account. Both read the same three places, and
 * a collection added to one without the other means the app either fails to
 * erase data or fails to disclose it.
 */
class ExportUserDataUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(userId: String): Result<UserDataExport> {
        if (userId.isBlank()) return Result.failure(AppError.SessionExpired())
        return userRepository.exportUserData(userId)
    }
}

/**
 * Uploads a new profile photo and records its URL on the user document.
 *
 * Two writes in a fixed order. The upload happens first so that a failure
 * leaves the profile pointing at the old photo rather than at nothing, which
 * would look like the picture was deleted rather than that the change failed.
 */
class UpdateAvatarUseCase @Inject constructor(
    private val avatarRepository: AvatarRepository,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(user: User, imageUri: String): Result<String> {
        if (user.userId.isBlank()) return Result.failure(AppError.SessionExpired())

        val url = avatarRepository.uploadAvatar(user.userId, imageUri)
            .getOrElse { return Result.failure(it) }

        return userRepository.updateUser(user.copy(photoUrl = url)).map { url }
    }
}
