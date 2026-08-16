package com.pamoja.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.pamoja.app.data.remote.model.GroupDto
import com.pamoja.app.data.remote.model.MembershipDto
import com.pamoja.app.data.remote.model.StepEntryDto
import com.pamoja.app.data.remote.model.UserDto
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.UserDataExport
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
     * Updates the profile, then fans the visible fields out to every membership
     * this user holds.
     *
     * This is the cost of denormalising onto memberships. It buys a large
     * saving on the read side, since leaderboards no longer read one user
     * document per member on every snapshot update, and it lets users/{userId}
     * stay owner-only in the security rules.
     *
     * **The photo fan-out is the privacy control, not a mirror of it.** When
     * [User.showPhotoInGroups] is off the membership is written with a blank
     * URL, so other members have nothing to fetch. Copying the URL across and
     * asking clients to hide it would leave the photo readable by anyone who
     * queried the collection directly.
     *
     * The fan-out is best effort. Group caps are 20, so the write count is
     * bounded and small. If it fails the profile is still updated and the user
     * shows their old name to other members until the next change.
     *
     * One consequence worth knowing: a failed fan-out after switching the photo
     * OFF leaves the old URL on the membership. Opting out is the direction
     * where that matters, so it is retried once before giving up.
     */
    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            val dto = UserDto.fromDomain(user)
            usersCollection.document(user.userId).set(dto).await()

            // Blank unless the user has opted in. This single expression is
            // what other members can and cannot see.
            val sharedPhotoUrl =
                if (user.showPhotoInGroups) user.photoUrl.orEmpty() else ""

            suspend fun fanOut() {
                val memberships = membershipsCollection
                    .whereEqualTo("userId", user.userId)
                    .get()
                    .await()

                if (!memberships.isEmpty) {
                    val batch = firestore.batch()
                    memberships.documents.forEach { doc ->
                        batch.update(
                            doc.reference,
                            mapOf(
                                "displayName" to user.name,
                                "photoUrl" to sharedPhotoUrl,
                            )
                        )
                    }
                    batch.commit().await()
                }
            }

            val fanOutResult = runCatching { fanOut() }
            // Retried only when opting out, because that is the case where
            // failing silently leaves a photo visible that the user just asked
            // to hide. Opting in failing merely means no photo yet.
            if (fanOutResult.isFailure && sharedPhotoUrl.isEmpty()) {
                runCatching { fanOut() }
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
    /**
     * Reads, never writes. The mirror of [deleteAllUserData], visiting the same
     * three places so the two cannot drift apart.
     *
     * Groups are fetched one at a time rather than with a whereIn, because
     * whereIn caps at thirty values and a member of thirty-one groups would get
     * a silently truncated export, which is worse than a slower one.
     */
    override suspend fun exportUserData(userId: String): Result<UserDataExport> {
        return try {
            val user = usersCollection.document(userId).get().await()
                .toObject(UserDto::class.java)?.toDomain()
                ?: return Result.failure(AppError.NotFound("No profile for this account"))

            val memberships = membershipsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(MembershipDto::class.java)?.toDomain() }

            val groups = memberships.mapNotNull { membership ->
                runCatching {
                    firestore.collection("groups")
                        .document(membership.groupId)
                        .get()
                        .await()
                        .toObject(GroupDto::class.java)
                        ?.toDomain()
                }.getOrNull()
            }

            val steps = firestore.collection("steps")
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(StepEntryDto::class.java)?.toDomain() }
                .sortedBy { it.date }

            Result.success(
                UserDataExport(
                    user = user,
                    memberships = memberships,
                    groups = groups,
                    steps = steps,
                    exportedAt = System.currentTimeMillis(),
                )
            )
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun deleteAllUserData(userId: String): Result<Unit> {
        return try {
            val memberships = membershipsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            for (membershipDoc in memberships.documents) {
                val groupId = membershipDoc.getString("groupId") ?: continue
                val groupRef = firestore.collection("groups").document(groupId)

                // Who could inherit this group, read BEFORE the transaction.
                //
                // Firestore transactions can get documents but cannot run
                // queries, so the candidate list has to be gathered first. The
                // successor is the longest-standing remaining member, sorted
                // here rather than with orderBy so this stays a single-field
                // query and needs no composite index.
                //
                // A race is possible: someone could join or leave between this
                // read and the transaction. The security rule re-checks that
                // the incoming admin genuinely holds a membership, so the worst
                // case is a rejected write, not a group handed to a stranger.
                val successorId: String? = runCatching {
                    membershipsCollection
                        .whereEqualTo("groupId", groupId)
                        .get()
                        .await()
                        .documents
                        .filter { it.getString("userId") != userId }
                        .minByOrNull { it.getLong("joinedAt") ?: Long.MAX_VALUE }
                        ?.getString("userId")
                }.getOrNull()

                firestore.runTransaction { transaction ->
                    val groupSnapshot = transaction.get(groupRef)
                    val isAdmin = groupSnapshot.getString("adminId") == userId

                    // A count of zero means nothing to decrement. Writing -1
                    // would also be rejected by the security rules, which require
                    // exactly one less and never below zero, and a rejection here
                    // would abort the whole deletion.
                    val currentCount = groupSnapshot.getLong("memberCount") ?: 0L

                    when {
                        !groupSnapshot.exists() -> Unit

                        // The admin is leaving and nobody is left to inherit.
                        // The group has no reachable members and no one who
                        // could ever administer it, so it goes with them rather
                        // than lingering as an unreachable document.
                        isAdmin && successorId == null -> transaction.delete(groupRef)

                        // The admin is leaving but the group lives on. Hand it
                        // to the longest-standing member in the same write that
                        // removes this one, so the group is never adminless.
                        //
                        // Previously this branch did not exist: the group kept
                        // an adminId pointing at a deleted auth user, and no one
                        // could change the target, manage the invite link or
                        // remove a member ever again.
                        isAdmin -> {
                            val newCount = (currentCount - 1).coerceAtLeast(0L)
                            transaction.update(
                                groupRef,
                                mapOf(
                                    "adminId" to successorId,
                                    "memberCount" to newCount,
                                    "inviteLinkActive" to true,
                                )
                            )
                        }

                        currentCount > 0 -> {
                            val cap = groupSnapshot.getLong("maxMemberCap") ?: Long.MAX_VALUE
                            val newCount = currentCount - 1

                            transaction.update(groupRef, "memberCount", newCount)

                            // A slot just opened, so the link works again.
                            if (newCount < cap) {
                                transaction.update(groupRef, "inviteLinkActive", true)
                            }
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