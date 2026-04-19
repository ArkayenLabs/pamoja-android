package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.UserRepository
import javax.inject.Inject

class CreateUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(user: User): Result<Unit> {
        if (user.userId.isBlank()) return Result.failure(Exception("User ID cannot be empty"))
        if (user.name.isBlank()) return Result.failure(Exception("Name cannot be empty"))
        return userRepository.createUser(user)
    }
}

class GetUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String): Result<User> {
        if (userId.isBlank()) return Result.failure(Exception("User ID cannot be empty"))
        return userRepository.getUser(userId)
    }
}

class UpdateUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(user: User): Result<Unit> {
        if (user.userId.isBlank()) return Result.failure(Exception("User ID cannot be empty"))
        if (user.name.isBlank()) return Result.failure(Exception("Name cannot be empty"))
        return userRepository.updateUser(user)
    }
}

class SaveDeviceTokenUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, token: String): Result<Unit> {
        if (userId.isBlank()) return Result.failure(Exception("User ID cannot be empty"))
        if (token.isBlank()) return Result.failure(Exception("Token cannot be empty"))
        return userRepository.saveDeviceToken(userId, token)
    }
}