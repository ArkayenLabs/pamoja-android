package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.RemovedMember
import com.pamoja.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    suspend fun createGroup(group: Group): Result<Group>
    suspend fun getGroup(groupId: String): Result<Group>
    fun observeGroup(groupId: String): Flow<Group> = kotlinx.coroutines.flow.flow {
        emit(getGroup(groupId).getOrThrow())
    }
    suspend fun updateGroup(group: Group): Result<Unit>
    suspend fun getGroupByInviteLink(inviteLink: String): Result<Group>
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>
    fun getGroupMembers(groupId: String): Flow<List<User>>

    /**
     * The group's memberships, which carry each member's weekly step total.
     *
     * Separate from [getGroupMembers] because the leaderboard needs the steps
     * and the group editor only needs the roster. Both read the same documents;
     * neither screen opens both listeners.
     *
     * The steps live on the membership rather than being read from the steps
     * collection, because that read could not be secured. A Firestore list rule
     * is checked against what the query constrains, and the leaderboard's query
     * named no group at all, so any rule permitting it also permitted any signed
     * in account to read every user's step history.
     */
    fun getGroupMemberships(groupId: String): Flow<List<Membership>>

    /**
     * Publishes the caller's own weekly total onto their membership.
     *
     * Only ever the caller's own: the rules pin a membership write to its owner,
     * so each member's figure is written by their own device after a sync. A
     * member who has not synced recently therefore shows their last known total,
     * which was already true before this data moved.
     *
     * [walkedDays] is the day-dot mask for the same week (see
     * [com.pamoja.app.domain.model.WalkedDays]); it is stamped with
     * [weekStart] as its own marker in the same write.
     */
    suspend fun publishMyWeeklySteps(
        groupId: String,
        userId: String,
        steps: Long,
        weekStart: String,
        todaySteps: Long,
        todayDate: String,
        walkedDays: Int,
    ): Result<Unit>

    suspend fun getMembership(userId: String, groupId: String): Result<Membership>
    fun getUserGroups(userId: String): Flow<List<Group>>
    suspend fun updateMemberCap(groupId: String, cap: Int): Result<Unit>
    suspend fun updateWeeklyTarget(groupId: String, target: Int): Result<Unit>
    suspend fun deactivateInviteLink(groupId: String): Result<Unit>

    /**
     * Writes the admin-editable settings, and only those.
     *
     * Deliberately not [updateGroup], which does a whole-document `set` from a
     * Group the caller is holding. That Group carries `weeklySteps` and
     * `weekStart`, a cache other members are writing concurrently, so saving
     * settings from a screen opened two minutes ago would roll the group's
     * progress back to whatever it was when the screen loaded.
     */
    suspend fun updateGroupSettings(
        groupId: String,
        name: String,
        weeklyTarget: Int,
        maxMemberCap: Int,
        canMembersEditTarget: Boolean,
        weekStartDay: String,
    ): Result<Unit>

    /**
     * Removes a member: another one as the admin, or yourself when leaving.
     *
     * Transactional for the same reason leaving is: the membership document and
     * the group's `memberCount` have to move together or the count drifts and
     * the cap stops meaning anything.
     *
     * [keepOut] also lists them in the group's removed list in the same commit,
     * so the group's link no longer lets them back in. Only the admin may.
     */
    suspend fun removeMember(groupId: String, userId: String, keepOut: Boolean): Result<Unit>

    /** The admin's view of who they removed, newest first. */
    fun observeRemovedMembers(groupId: String): Flow<List<RemovedMember>>

    /** Takes someone off the removed list, so the link works for them again. */
    suspend fun letBackIn(groupId: String, userId: String): Result<Unit>

    /** Whether the admin removed [userId] from [groupId] and has not let them back in. */
    suspend fun isRemovedFrom(groupId: String, userId: String): Result<Boolean>

    /** Permanently removes an admin-owned group and all group-scoped records. */
    suspend fun deleteGroup(groupId: String): Result<Unit>

    /**
     * What an invite shows before sign-in, from the server: the group cannot
     * be read without an account. [AppError.NotFound] when it leads nowhere.
     */
    suspend fun getInviteSummary(code: String): Result<com.pamoja.app.domain.model.InviteSummary>

    /** Points the group at an already-uploaded photo, or clears it when blank. */
    suspend fun updateGroupPhoto(groupId: String, photoUrl: String): Result<Unit>

    /**
     * Publishes the cached weekly total for a group the caller is a member of.
     *
     * Writes the two cache fields only, never the whole document. Every member's
     * device recomputes and republishes the same figure, so writes race but do
     * not conflict: each one carries a full recomputation rather than a delta,
     * which makes a lost write merely stale instead of wrong.
     */
    suspend fun publishWeeklyTotal(
        groupId: String,
        weeklySteps: Long,
        weekStart: String,
    ): Result<Unit>
}
