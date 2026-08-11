package com.pamoja.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.pamoja.app.data.remote.model.UserDto
import com.pamoja.app.domain.error.AppError
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
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun getUser(userId: String): Result<User> {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            val dto = snapshot.toObject(UserDto::class.java)
                ?: return Result.failure(AppError.NotFound("User document missing"))
            Result.success(dto.toDomain())
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
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
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun saveDeviceToken(userId: String, token: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("deviceToken", token)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    /**
     * Order matters throughout.
     *
     * Memberships go first, each in its own transaction, because leaving a group
     * is not just a delete: the group's memberCount has to come down with it or
     * every remaining group is permanently one member over its real size and can
     * refuse joins forever. Freeing a slot also revives the invite link, which
     * is otherwise switched off at the cap and never switched back on.
     *
     * Steps are batched, since there is one document per user per day and a
     * long-lived account can exceed Firestore's 500 writes per batch.
     *
     * The profile document goes last so that a failure part way through leaves
     * the user still able to sign in and retry, rather than stranded with a
     * working account and no profile.
     */
    override suspend fun deleteAllUserData(userId: String): Result<Unit> {
        return try {
            val memberships = membershipsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            for (membershipDoc in memberships.documents) {
                val groupId = membershipDoc.getString("groupId") ?: continue
                val groupRef = firestore.collection("groups").document(groupId)

                firestore.runTransaction { transaction ->
                    val groupSnapshot = transaction.get(groupRef)

                    // A count of zero means nothing to decrement. Writing -1
                    // would also be rejected by the security rules, which require
                    // exactly one less and never below zero, and a rejection here
                    // would abort the whole deletion.
                    val currentCount = groupSnapshot.getLong("memberCount") ?: 0L
                    if (groupSnapshot.exists() && currentCount > 0) {
                        val cap = groupSnapshot.getLong("maxMemberCap") ?: Long.MAX_VALUE
                        val newCount = currentCount - 1

                        transaction.update(groupRef, "memberCount", newCount)

                        // A slot just opened, so the link works again.
                        if (newCount < cap) {
                            transaction.update(groupRef, "inviteLinkActive", true)
                        }
                    }

                    transaction.delete(membershipDoc.reference)
                }.await()
            }

            val steps = firestore.collection("steps")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            steps.documents.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            usersCollection.document(userId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }
}