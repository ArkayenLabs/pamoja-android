package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.UserDataExport

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

    /**
     * Gathers everything held about this user, for export.
     *
     * Reads the same three places [deleteAllUserData] destroys. Keep the two in
     * step: a collection added to one and forgotten in the other means the app
     * either fails to erase data or fails to disclose it.
     */
    suspend fun exportUserData(userId: String): Result<UserDataExport>
}