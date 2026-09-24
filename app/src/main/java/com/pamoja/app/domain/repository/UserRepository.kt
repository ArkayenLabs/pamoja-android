package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.UserDataExport

interface UserRepository {
    suspend fun createUser(user: User): Result<Unit>
    suspend fun getUser(userId: String): Result<User>
    suspend fun updateUser(user: User): Result<Unit>

    /**
     * Erases client-owned data: the profile document, every membership, and
     * every step entry. Deleting the profile also triggers the trusted backend
     * cleanup for server-owned push registrations.
     *
     * Must run BEFORE the Firebase Auth account is deleted. Once the account is
     * gone `request.auth` is null and the security rules reject these writes,
     * which would leave the data orphaned forever with no way to reach it.
     */
    suspend fun deleteAllUserData(userId: String): Result<Unit>

    /**
     * Gathers everything held about this user, for export.
     *
     * Includes the server-owned push registrations that profile deletion asks
     * the backend to erase. Keep export and deletion coverage in step.
     */
    suspend fun exportUserData(userId: String): Result<UserDataExport>
}
