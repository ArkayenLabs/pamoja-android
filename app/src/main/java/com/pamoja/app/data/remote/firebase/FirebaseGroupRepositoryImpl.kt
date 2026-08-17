package com.pamoja.app.data.remote.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pamoja.app.data.remote.model.GroupDto
import com.pamoja.app.data.remote.model.MembershipDto
import com.pamoja.app.data.remote.model.UserDto
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.util.InviteLink
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

    /**
     * Reads a user's display name, for copying onto a membership document.
     *
     * Called only when a membership is created, so it is one read at join time
     * rather than one read per member on every leaderboard render.
     */
    private suspend fun displayNameOf(userId: String): String = runCatching {
        usersCollection.document(userId).get().await()
            .toObject(UserDto::class.java)?.name.orEmpty()
    }.getOrDefault("")

    /**
     * The photo to stamp onto a new membership, honouring the owner's choice.
     *
     * Blank unless they have opted in, so joining a group never leaks a photo
     * the user has not agreed to show. Blank on any failure too, since the
     * private outcome is the safe one to fall back to.
     */
    private suspend fun sharedPhotoOf(userId: String): String = runCatching {
        val user = usersCollection.document(userId).get().await()
            .toObject(UserDto::class.java) ?: return ""
        if (user.showPhotoInGroups) user.photoUrl.orEmpty() else ""
    }.getOrDefault("")

    override suspend fun createGroup(group: Group): Result<Group> {
        return try {
            val dto = GroupDto.fromDomain(group)
            groupsCollection.document(group.groupId).set(dto).await()
            
            // Add admin membership, carrying a copy of their display name so
            // member lists never need to read user documents.
            val membership = MembershipDto(
                userId = group.adminId,
                groupId = group.groupId,
                displayName = displayNameOf(group.adminId),
                photoUrl = sharedPhotoOf(group.adminId),
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
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun getGroup(groupId: String): Result<Group> {
        return try {
            val snapshot = groupsCollection.document(groupId).get().await()
            val dto = snapshot.toObject(GroupDto::class.java)
                ?: return Result.failure(AppError.NotFound("Group document missing"))
            Result.success(dto.toDomain())
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun updateGroup(group: Group): Result<Unit> {
        return try {
            val dto = GroupDto.fromDomain(group)
            groupsCollection.document(group.groupId).set(dto).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    /**
     * Resolves a group from any accepted invite input: the verified https link,
     * the legacy pamoja:// link, or a bare code.
     *
     * Deliberately a direct document read rather than a collection query.
     * The previous implementation ran whereEqualTo("inviteLink", ...), which had
     * three problems:
     *
     *  1. It required `list` permission on the groups collection in the security
     *     rules, and that permits enumerating every group in the database.
     *  2. It broke whenever the link format changed, because existing documents
     *     store the old pamoja:// string.
     *  3. The stored link duplicated information already present in the document
     *     ID, so the two could drift apart.
     *
     * The invite code already is the group ID, so a direct get is both cheaper
     * and format independent. Old and new links resolve identically with no
     * data migration.
     */
    override suspend fun getGroupByInviteLink(inviteLink: String): Result<Group> {
        return try {
            val code = InviteLink.parseCode(inviteLink) ?: inviteLink.trim()
            if (code.isBlank()) {
                return Result.failure(AppError.Validation(ValidationField.InviteCodeMalformed))
            }

            val snapshot = groupsCollection.document(code).get().await()
            val dto = snapshot.toObject(GroupDto::class.java)
                ?: return Result.failure(AppError.NotFound("Invite code resolved to no group"))

            // Resolves regardless of inviteLinkActive, and JoinGroupUseCase
            // enforces it instead. Refusing here meant a full group could not
            // even be read, so the join preview had no name or member count to
            // show and had to fall back to a generic failure. Resolution is a
            // read; whether joining is permitted is policy, and belongs above.
            Result.success(dto.toDomain())
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    // ── Fix 2: Atomic join with memberCount ─────────────────────────────────────
    //
    // Why a schema change is needed:
    //   Firestore transactions can only read individual documents, they cannot
    //   run collection queries. The previous implementation counted memberships
    //   via a query and then wrote the membership document in a separate step,
    //   creating a TOCTOU race condition where concurrent joins could exceed
    //   maxMemberCap.
    //
    // Solution:
    //   Store memberCount on the group document and use a Firestore transaction
    //   to atomically read the count, validate the cap, increment the count,
    //   and write the membership, all in one atomic operation.
    //
    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            val groupDocRef = groupsCollection.document(groupId)
            val membershipDocRef = membershipsCollection.document("${userId}_${groupId}")

            // Read outside the transaction. Firestore transactions cannot read
            // other documents lazily, and neither the name nor the photo is
            // part of the atomic invariant we are protecting, which is only the
            // member cap.
            val joinerName = displayNameOf(userId)
            val joinerPhoto = sharedPhotoOf(userId)

            firestore.runTransaction { transaction ->
                val groupSnapshot = transaction.get(groupDocRef)
                val group = groupSnapshot.toObject(GroupDto::class.java)
                    ?: throw Exception("Group not found")

                // Idempotent join. Reopening your own invite must not double count.
                val existingMembership = transaction.get(membershipDocRef)
                if (existingMembership.exists()) {
                    return@runTransaction
                }

                // Typed, so the join screen can tell "full" apart from a generic
                // failure and offer the right dead end rather than a Retry.
                if (group.memberCount >= group.maxMemberCap) {
                    throw AppError.Conflict("Group is full")
                }

                val membership = MembershipDto(
                    userId = userId,
                    groupId = groupId,
                    displayName = joinerName,
                    photoUrl = joinerPhoto,
                    role = "member",
                    canEditTarget = false,
                    joinedAt = System.currentTimeMillis()
                )
                transaction.set(membershipDocRef, membership)

                val newCount = group.memberCount + 1
                transaction.update(groupDocRef, "memberCount", newCount)

                // Close the invite once the group is full.
                //
                // Reopened by deleteAllUserData when an account leaves and frees
                // a slot. If any other way of leaving a group is added later, it
                // must do the same, or a group that fills once can never be
                // joined again.
                if (newCount >= group.maxMemberCap) {
                    transaction.update(groupDocRef, "inviteLinkActive", false)
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
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

                // Built entirely from the membership documents. This used to
                // fan out to one users/{id} read per member, and because it sat
                // inside a snapshot listener it re-ran on every membership
                // change, so an 8 member group cost 8 extra reads every time
                // anything moved.
                //
                // The name now lives on the membership itself, so a member list
                // is a single query. Only fields other members are entitled to
                // see are populated here; age, height and weight never leave the
                // owner's own document.
                val members = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(MembershipDto::class.java)?.let { m ->
                        User(
                            userId = m.userId,
                            name = m.displayName,
                            // Blank whenever that member opted out, because the
                            // fan-out never wrote it. Mapped to null so the
                            // avatar falls back to initials.
                            photoUrl = m.photoUrl.takeIf { it.isNotBlank() },
                        )
                    }
                }
                trySend(members)
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
                ?: return Result.failure(AppError.NotFound("Membership document missing"))
            Result.success(dto.toDomain())
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
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
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun updateWeeklyTarget(groupId: String, target: Int): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update("weeklyTarget", target)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun updateGroupSettings(
        groupId: String,
        name: String,
        weeklyTarget: Int,
        maxMemberCap: Int,
        canMembersEditTarget: Boolean,
        weekStartDay: String,
    ): Result<Unit> {
        return try {
            // A field map, not a DTO set. The security rule permits exactly
            // these keys to move for an admin, and writing the whole document
            // would also carry the caller's stale copy of the weekly step
            // cache back over whatever other members have since published.
            groupsCollection.document(groupId)
                .update(
                    mapOf(
                        "name" to name,
                        "weeklyTarget" to weeklyTarget,
                        "maxMemberCap" to maxMemberCap,
                        "canMembersEditTarget" to canMembersEditTarget,
                        "weekStartDay" to weekStartDay,
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun removeMember(groupId: String, userId: String): Result<Unit> {
        return try {
            val groupRef = groupsCollection.document(groupId)
            val membershipRef = firestore.collection("memberships")
                .document("${userId}_$groupId")

            firestore.runTransaction { transaction ->
                val groupSnapshot = transaction.get(groupRef)
                val membershipSnapshot = transaction.get(membershipRef)

                // Already gone. Treated as success rather than an error: two
                // taps on Remove, or the member leaving on their own device
                // first, should not surface a failure for an outcome the admin
                // wanted anyway.
                if (!membershipSnapshot.exists()) return@runTransaction

                val currentCount = groupSnapshot.getLong("memberCount") ?: 0L
                if (groupSnapshot.exists() && currentCount > 0) {
                    val cap = groupSnapshot.getLong("maxMemberCap") ?: Long.MAX_VALUE
                    val newCount = currentCount - 1

                    transaction.update(groupRef, "memberCount", newCount)

                    // The freed slot revives the link, matching what leaving does.
                    if (newCount < cap) {
                        transaction.update(groupRef, "inviteLinkActive", true)
                    }
                }

                transaction.delete(membershipRef)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun updateGroupPhoto(groupId: String, photoUrl: String): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update("photoUrl", photoUrl)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun deactivateInviteLink(groupId: String): Result<Unit> {
        return try {
            groupsCollection.document(groupId)
                .update("inviteLinkActive", false)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    override suspend fun publishWeeklyTotal(
        groupId: String,
        weeklySteps: Long,
        weekStart: String,
    ): Result<Unit> {
        return try {
            // A field-level update, not set(). The rule that permits this only
            // allows these two keys to change, and writing the whole document
            // would both fail that rule and risk the set()-clobber that erased
            // profile fields once already.
            groupsCollection.document(groupId)
                .update(
                    mapOf(
                        "weeklySteps" to weeklySteps,
                        "weekStart" to weekStart,
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }
}