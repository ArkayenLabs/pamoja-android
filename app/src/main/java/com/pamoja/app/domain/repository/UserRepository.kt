package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.User

interface UserRepository {
    suspend fun createUser(user: User): Result<Unit>
    suspend fun getUser(userId: String): Result<User>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun saveDeviceToken(userId: String, token: String): Result<Unit>
}