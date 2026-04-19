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
}