package com.pamoja.app.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.AuthRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth
) : AuthRepository {

    override suspend fun signUp(email: String, password: String): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
                ?: return Result.failure(Exception("User creation failed"))
            Result.success(
                User(userId = firebaseUser.uid)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
                ?: return Result.failure(Exception("Sign in failed"))
            Result.success(
                User(userId = firebaseUser.uid)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return User(userId = firebaseUser.uid)
    }

    override suspend fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            auth.currentUser?.delete()?.await()
                ?: return Result.failure(Exception("No user logged in"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}