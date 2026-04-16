package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.User

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<User>
    suspend fun signIn(email: String, password: String): Result<User>
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentUser(): User?
    suspend fun isUserLoggedIn(): Boolean
    suspend fun deleteAccount(): Result<Unit>
}