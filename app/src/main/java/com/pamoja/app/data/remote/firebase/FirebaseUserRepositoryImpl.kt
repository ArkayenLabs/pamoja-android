package com.pamoja.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.pamoja.app.data.remote.model.UserDto
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.UserRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseUserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : UserRepository {

    private val usersCollection = firestore.collection("users")

    override suspend fun createUser(user: User): Result<Unit> {
        return try {
            val dto = UserDto.fromDomain(user)
            usersCollection.document(user.userId).set(dto).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUser(userId: String): Result<User> {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            val dto = snapshot.toObject(UserDto::class.java)
                ?: return Result.failure(Exception("User not found"))
            Result.success(dto.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            val dto = UserDto.fromDomain(user)
            usersCollection.document(user.userId).set(dto).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveDeviceToken(userId: String, token: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("deviceToken", token)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}