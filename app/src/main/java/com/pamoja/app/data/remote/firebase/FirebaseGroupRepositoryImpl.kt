package com.pamoja.app.data.remote.firebase

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

    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            val groupResult = getGroup(groupId)
            val group = groupResult.getOrElse {
                return Result.failure(it)
            }

            val memberCount = membershipsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
                .size()

            if (memberCount >= group.maxMemberCap) {
                return Result.failure(Exception("Group is full"))
            }

            val membership = MembershipDto(
                userId = userId,
                groupId = groupId,
                role = "member",
                canEditTarget = false,
                joinedAt = System.currentTimeMillis()
            )
            membershipsCollection
                .document("${userId}_${groupId}")
                .set(membership)
                .await()

            if (memberCount + 1 >= group.maxMemberCap) {
                deactivateInviteLink(groupId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override  fun getGroupMembers(groupId: String): Flow<List<User>> = callbackFlow {
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

                firestore.runTransaction { transaction ->
                    userIds.mapNotNull { userId ->
                        transaction.get(usersCollection.document(userId))
                            .toObject(UserDto::class.java)
                            ?.toDomain()
                    }
                }.addOnSuccessListener { users ->
                    trySend(users)
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

                firestore.runTransaction { transaction ->
                    groupIds.mapNotNull { groupId ->
                        transaction.get(groupsCollection.document(groupId))
                            .toObject(GroupDto::class.java)
                            ?.toDomain()
                    }
                }.addOnSuccessListener { groups ->
                    trySend(groups)
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