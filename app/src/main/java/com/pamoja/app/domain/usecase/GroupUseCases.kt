package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
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
        if (name.isBlank()) return Result.failure(AppError.Validation(ValidationField.GroupNameMissing))
        if (adminId.isBlank()) return Result.failure(AppError.SessionExpired())
        if (weeklyTarget <= 0) return Result.failure(AppError.Validation(ValidationField.WeeklyTargetInvalid))
        if (maxMemberCap < 2) return Result.failure(AppError.Validation(ValidationField.MemberCapTooSmall))

        val groupId = UUID.randomUUID().toString()
        val inviteLink = "pamoja://join/$groupId"

        val group = Group(
            groupId = groupId,
            name = name,
            adminId = adminId,
            weeklyTarget = weeklyTarget,
            maxMemberCap = maxMemberCap,
            memberCount = 1,  // Admin is the first member
            canMembersEditTarget = canMembersEditTarget,
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
        if (groupId.isBlank()) return Result.failure(AppError.Validation(ValidationField.InviteCodeMissing))
        return groupRepository.getGroup(groupId)
    }
}

class JoinGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(inviteLink: String, userId: String): Result<Unit> {
        if (inviteLink.isBlank()) {
            return Result.failure(AppError.Validation(ValidationField.InviteCodeMissing))
        }
        if (userId.isBlank()) return Result.failure(AppError.SessionExpired())

        val group = groupRepository.getGroupByInviteLink(inviteLink).getOrElse {
            return Result.failure(it)
        }

        if (!group.inviteLinkActive) {
            return Result.failure(AppError.Conflict("Invite link is not active"))
        }

        return groupRepository.joinGroup(group.groupId, userId)
    }
}

/**
 * What a person sees before committing to a join.
 *
 * [isAlreadyMember] and [isFull] are resolved here rather than being discovered
 * by attempting the join and reading the failure, because a preview that can
 * only tell you it failed is not a preview.
 */
data class InvitePreview(
    val group: Group,
    val isAlreadyMember: Boolean,
    val isFull: Boolean,
)

/**
 * Resolves an invite without joining anything.
 *
 * Deliberately read-only. Opening a link used to join silently and land the
 * user in a group they had not agreed to be in.
 */
class ResolveInviteUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(codeOrLink: String, userId: String): Result<InvitePreview> {
        if (codeOrLink.isBlank()) {
            return Result.failure(AppError.Validation(ValidationField.InviteCodeMissing))
        }

        val group = groupRepository.getGroupByInviteLink(codeOrLink).getOrElse {
            return Result.failure(it)
        }

        val isAlreadyMember =
            groupRepository.getMembership(userId, group.groupId).getOrNull() != null

        return Result.success(
            InvitePreview(
                group = group,
                isAlreadyMember = isAlreadyMember,
                // Being a member already means the cap is irrelevant: they are
                // inside it. Otherwise a full group they belong to would show
                // as a dead end instead of offering the way in.
                isFull = !isAlreadyMember && group.memberCount >= group.maxMemberCap,
            )
        )
    }
}

class GetGroupMembersUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(groupId: String): Flow<List<User>> {
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
        if (target <= 0) return Result.failure(AppError.Validation(ValidationField.WeeklyTargetInvalid))

        val membership = groupRepository.getMembership(userId, groupId).getOrElse {
            return Result.failure(it)
        }

        val isAdmin = group.adminId == userId
        val canEdit = group.canMembersEditTarget

        if (!isAdmin && !canEdit) {
            return Result.failure(AppError.Validation(ValidationField.NotAllowedToEditTarget))
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