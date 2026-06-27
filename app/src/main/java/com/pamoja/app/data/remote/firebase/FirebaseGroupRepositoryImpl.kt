package com.pamoja.app.data.remote.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pamoja.app.data.remote.model.GroupDto
import com.pamoja.app.data.remote.model.MembershipDto
import com.pamoja.app.data.remote.model.UserDto
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.GroupRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseGroupRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : GroupRepository {

    private val groupsCollection = firestore.collection("groups")
    private val membershipsCollection = firestore.collection("memberships")
    private val usersCollection = firestore.collection("users")

    override suspend fun createGroup(group: Group): Result<Group> {
        return try {
            val dto = GroupDto.fromDomain(group)
            groupsCollection.document(group.groupId).set(dto).await()
            
            // Add admin membership
            val membership = MembershipDto(
                userId = group.adminId,
                groupId = group.groupId,
                role = "admin",
                canEditTarget = true,
                joinedAt = System.currentTimeMillis()
            )
            membershipsCollection
                .document("${group.adminId}_${group.groupId}")
                .set(membership)
                .await()
                
            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroup(groupId: String): Result<Group> {
        return try {
            val snapshot = groupsCollection.document(groupId).get().await()
            val dto = snapshot.toObject(GroupDto::class.java)
                ?: return Result.failure(Exception("Group not found"))
            Result.success(dto.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGroup(group: Group): Result<Unit> {
        return try {
            val dto = GroupDto.fromDomain(group)
            groupsCollection.document(group.groupId).set(dto).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupByInviteLink(inviteLink: String): Result<Group> {
        return try {
            val snapshot = groupsCollection
                .whereEqualTo("inviteLink", inviteLink)
                .whereEqualTo("inviteLinkActive", true)
                .get()
                .await()
            val dto = snapshot.documents.firstOrNull()?.toObject(GroupDto::class.java)
                ?: return Result.failure(Exception("Invalid or expired invite link"))
            Result.success(dto.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Fix 2: Atomic join with memberCount ─────────────────────────────────────
    //
    // Why a schema change is needed:
    //   Firestore transactions can only read individual documents — they cannot
    //   run collection queries. The previous implementation counted memberships
    //   via a query and then wrote the membership document in a separate step,
    //   creating a TOCTOU race condition where concurrent joins could exceed
    //   maxMemberCap.
    //
    // Solution:
    //   Store memberCount on the group document and use a Firestore transaction
    //   to atomically read the count, validate the cap, increment the count,
    //   and write the membership — all in one atomic operation.
    //
    // Migration:
    //   Existing groups created before this change will have memberCount = 0
    //   (Kotlin default for missing Firestore fields). The transaction handles
    //   this by falling back to a query-based count when memberCount is 0,
    //   backfilling the field atomically on the first join.

    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            val groupDocRef = groupsCollection.document(groupId)
            val membershipDocRef = membershipsCollection.document("${userId}_${groupId}")

            // For legacy groups without memberCount, pre-fetch the actual count
            // so the transaction can backfill it. This query runs outside the
            // transaction (acceptable: the transaction still validates atomically).
            val legacyCount: Int? = run {
                val groupSnap = groupDocRef.get().await()
                val dto = groupSnap.toObject(GroupDto::class.java)
                    ?: return Result.failure(Exception("Group not found"))
                if (dto.memberCount == 0 && dto.adminId.isNotEmpty()) {
                    // memberCount is 0 but the group has an admin — likely a legacy group.
                    // Count actual memberships for backfill.
                    membershipsCollection
                        .whereEqualTo("groupId", groupId)
                        .get()
                        .await()
                        .size()
                } else {
                    null  // memberCount is already accurate, no backfill needed
                }
            }

            firestore.runTransaction { transaction ->
                val groupSnapshot = transaction.get(groupDocRef)
                val group = groupSnapshot.toObject(GroupDto::class.java)
                    ?: throw Exception("Group not found")

                // Check if already a member (idempotent join)
                val existingMembership = transaction.get(membershipDocRef)
                if (existingMembership.exists()) {
                    return@runTransaction  // Already joined — no-op
                }

                // Determine current count: use backfilled value for legacy groups
                val currentCount = if (group.memberCount == 0 && legacyCount != null) {
                    legacyCount
                } else {
                    group.memberCount
                }

                if (currentCount >= group.maxMemberCap) {
                    throw Exception("Group is full")
                }

                // Write membership
                val membership = MembershipDto(
                    userId = userId,
                    groupId = groupId,
                    role = "member",
                    canEditTarget = false,
                    joinedAt = System.currentTimeMillis()
                )
                transaction.set(membershipDocRef, membership)

                // Atomically update member count (and backfill for legacy groups)
                val newCount = currentCount + 1
                transaction.update(groupDocRef, "memberCount", newCount)

                // Deactivate invite link if cap is now reached
                if (newCount >= group.maxMemberCap) {
                    transaction.update(groupDocRef, "inviteLinkActive", false)
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Fix 1: Replace runTransaction with parallel document reads ───────────────
    //
    // Previous implementation used runTransaction() for read-only batch fetches,
    // which is incorrect (transactions are for atomic read-write operations) and
    // silently swallowed failures due to missing addOnFailureListener.
    //
    // New implementation uses Tasks.whenAllSuccess() to fetch all documents in
    // parallel, with proper error handling on both success and failure paths.

    override fun getGroupMembers(groupId: String): Flow<List<User>> = callbackFlow {
        val listener = membershipsCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val userIds = snapshot.documents.mapNotNull {
                    it.toObject(MembershipDto::class.java)?.userId
                }

                if (userIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val tasks = userIds.map { userId ->
                    usersCollection.document(userId).get()
                }
                Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
                    .addOnSuccessListener { snapshots ->
                        val users = snapshots.mapNotNull {
                            it.toObject(UserDto::class.java)?.toDomain()
                        }
                        trySend(users)
                    }
                    .addOnFailureListener { e ->
                        close(e)
                    }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getMembership(userId: String, groupId: String): Result<Membership> {
        return try {
            val snapshot = membershipsCollection
                .document("${userId}_${groupId}")
                .get()
                .await()
            val dto = snapshot.toObject(MembershipDto::class.java)
                ?: return Result.failure(Exception("Membership not found"))
            Result.success(dto.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getUserGroups(userId: String): Flow<List<Group>> = callbackFlow {
        val listener = membershipsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val groupIds = snapshot.documents.mapNotNull {
                    it.toObject(MembershipDto::class.java)?.groupId
                }

                if (groupIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val tasks = groupIds.map { groupId ->
                    groupsCollection.document(groupId).get()
                }
                Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
                    .addOnSuccessListener { snapshots ->
                        val groups = snapshots.mapNotNull {
                            it.toObject(GroupDto::class.java)?.toDomain()
                        }
                        trySend(groups)
                    }
                    .addOnFailureListener { e ->
                        close(e)
                    }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateMemberCap(groupId: String, cap: Int): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update("maxMemberCap", cap)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateWeeklyTarget(groupId: String, target: Int): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update("weeklyTarget", target)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deactivateInviteLink(groupId: String): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update("inviteLinkActive", false)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}