package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    suspend fun createGroup(group: Group): Result<Group>
    suspend fun getGroup(groupId: String): Result<Group>
    suspend fun updateGroup(group: Group): Result<Unit>
    suspend fun getGroupByInviteLink(inviteLink: String): Result<Group>
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>
    fun getGroupMembers(groupId: String): Flow<List<User>>
    suspend fun getMembership(userId: String, groupId: String): Result<Membership>
    fun getUserGroups(userId: String): Flow<List<Group>>
    suspend fun updateMemberCap(groupId: String, cap: Int): Result<Unit>
    suspend fun updateWeeklyTarget(groupId: String, target: Int): Result<Unit>
    suspend fun deactivateInviteLink(groupId: String): Result<Unit>

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