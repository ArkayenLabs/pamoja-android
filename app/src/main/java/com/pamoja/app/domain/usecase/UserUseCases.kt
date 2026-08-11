package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
import com.pamoja.app.domain.model.User
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