package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.User

interface UserRepository {
    suspend fun createUser(user: User): Result<Unit>
    suspend fun getUser(userId: String): Result<User>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun saveDeviceToken(userId: String, token: String): Result<Unit>

    /**
     * Erases everything this user owns: the profile document, every membership,
     * and every step entry.
     *
     * Must run BEFORE the Firebase Auth account is deleted. Once the account is
     * gone `request.auth` is null and the security rules reject these writes,
     * which would leave the data orphaned forever with no way to reach it.
     */
    suspend fun deleteAllUserData(userId: String): Result<Unit>
}