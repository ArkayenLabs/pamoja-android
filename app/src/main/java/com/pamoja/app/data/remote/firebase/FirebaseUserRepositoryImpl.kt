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
    private val membershipsCollection = firestore.collection("memberships")

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

    /**
     * Updates the profile, then fans the display name out to every membership
     * this user holds.
     *
     * This is the cost of denormalising the name onto memberships. It buys a
     * large saving on the read side, since leaderboards no longer read one user
     * document per member on every snapshot update, and it lets users/{userId}
     * stay owner-only in the security rules.
     *
     * The fan-out is best effort. Group caps are 20, so the write count is
     * bounded and small. If it fails the profile is still updated and the user
     * simply shows their old name to other members until the next rename.
     */
    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            val dto = UserDto.fromDomain(user)
            usersCollection.document(user.userId).set(dto).await()

            runCatching {
                val memberships = membershipsCollection
                    .whereEqualTo("userId", user.userId)
                    .get()
                    .await()

                if (!memberships.isEmpty) {
                    val batch = firestore.batch()
                    memberships.documents.forEach { doc ->
                        batch.update(doc.reference, "displayName", user.name)
                    }
                    batch.commit().await()
                }
            }

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