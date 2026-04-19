package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(
        name: String,
        adminId: String,
        weeklyTarget: Int,
        maxMemberCap: Int,
        canMembersEditTarget: Boolean
    ): Result<Group> {
        if (name.isBlank()) return Result.failure(Exception("Group name cannot be empty"))
        if (adminId.isBlank()) return Result.failure(Exception("Admin ID cannot be empty"))
        if (weeklyTarget <= 0) return Result.failure(Exception("Weekly target must be greater than 0"))
        if (maxMemberCap < 2) return Result.failure(Exception("Group must allow at least 2 members"))

        val groupId = UUID.randomUUID().toString()
        val inviteLink = "pamoja://join/$groupId"

        val group = Group(
            groupId = groupId,
            name = name,
            adminId = adminId,
            weeklyTarget = weeklyTarget,
            maxMemberCap = maxMemberCap,
            inviteLink = inviteLink,
            inviteLinkActive = true,
            createdAt = System.currentTimeMillis()
        )
        return groupRepository.createGroup(group)
    }
}

class GetGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String): Result<Group> {
        if (groupId.isBlank()) return Result.failure(Exception("Group ID cannot be empty"))
        return groupRepository.getGroup(groupId)
    }
}

class JoinGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(inviteLink: String, userId: String): Result<Unit> {
        if (inviteLink.isBlank()) return Result.failure(Exception("Invite link cannot be empty"))
        if (userId.isBlank()) return Result.failure(Exception("User ID cannot be empty"))

        val groupResult = groupRepository.getGroupByInviteLink(inviteLink)
        val group = groupResult.getOrElse {
            return Result.failure(it)
        }

        if (!group.inviteLinkActive) {
            return Result.failure(Exception("This invite link is no longer active"))
        }

        return groupRepository.joinGroup(group.groupId, userId)
    }
}

class GetGroupMembersUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String): Flow<List<User>> {
        return groupRepository.getGroupMembers(groupId)
    }
}

class GetUserGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(userId: String): Flow<List<Group>> {
        return groupRepository.getUserGroups(userId)
    }
}

class UpdateWeeklyTargetUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(
        groupId: String,
        target: Int,
        userId: String,
        group: Group
    ): Result<Unit> {
        if (target <= 0) return Result.failure(Exception("Target must be greater than 0"))

        val membership = groupRepository.getMembership(userId, groupId).getOrElse {
            return Result.failure(it)
        }

        val isAdmin = group.adminId == userId
        val canEdit = membership.canEditTarget

        if (!isAdmin && !canEdit) {
            return Result.failure(Exception("You don't have permission to edit the target"))
        }

        return groupRepository.updateWeeklyTarget(groupId, target)
    }
}

class GetMembershipUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(userId: String, groupId: String): Result<Membership> {
        return groupRepository.getMembership(userId, groupId)
    }
}