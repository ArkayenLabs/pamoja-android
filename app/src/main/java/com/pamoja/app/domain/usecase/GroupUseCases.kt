package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    /**
     * [weekStartDay] defaults to the creating device's locale, which is the
     * right guess and the one every comparable app makes. It is stamped onto
     * the group rather than resolved per member, so everyone in the group shares
     * one week regardless of where they are.
     */
    suspend operator fun invoke(
        name: String,
        adminId: String,
        weeklyTarget: Int,
        maxMemberCap: Int,
        canMembersEditTarget: Boolean,
        weekStartDay: DayOfWeek = WeekWindow.localeDefault(),
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
            createdAt = System.currentTimeMillis(),
            weekStartDay = weekStartDay.name,
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

/**
 * Recomputes and publishes one group's cached weekly total.
 *
 * Deliberately a full recomputation rather than an increment. Several members
 * may publish at once and an increment would double-count under a race, whereas
 * a recomputation is idempotent: the worst a concurrent write can do is land a
 * figure that is a few minutes old.
 */
class PublishGroupWeeklyTotalUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        weeklySteps: Long,
        startDay: DayOfWeek,
    ): Result<Unit> {
        if (groupId.isBlank()) return Result.success(Unit)
        return groupRepository.publishWeeklyTotal(
            groupId = groupId,
            weeklySteps = weeklySteps.coerceAtLeast(0L),
            // Stamped with the group's own week, so isCurrent compares like
            // with like. Stamping the device's would mark the cache stale for
            // every member whose locale differs from the group's setting.
            weekStart = WeekWindow.startOf(startDay),
        )
    }
}

/**
 * Edits an existing group, as the admin.
 *
 * Until this existed nothing about a group could be changed after creation: not
 * the name, not the goal, not the size, not who was in it. A typo in a group
 * name was permanent, and a goal chosen before anyone had walked a step could
 * never be corrected.
 *
 * Validation mirrors [CreateGroupUseCase] so a value creation would have
 * rejected cannot arrive here instead, plus the one rule that only applies to
 * editing: the cap cannot fall below the people already in the group.
 */
class UpdateGroupSettingsUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(
        group: Group,
        editorId: String,
        name: String,
        weeklyTarget: Int,
        maxMemberCap: Int,
        canMembersEditTarget: Boolean,
        weekStartDay: DayOfWeek,
    ): Result<Unit> {
        // Checked here as well as in the security rules. The rules are what
        // actually protect the data; this is what produces a sensible message
        // instead of a permission denial the user cannot act on.
        if (group.adminId != editorId) {
            return Result.failure(AppError.PermissionDenied())
        }

        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return Result.failure(AppError.Validation(ValidationField.GroupNameMissing))
        }
        if (trimmed.length > MAX_GROUP_NAME_LENGTH) {
            return Result.failure(AppError.Validation(ValidationField.GroupNameTooLong))
        }
        if (weeklyTarget <= 0 || weeklyTarget > MAX_WEEKLY_TARGET) {
            return Result.failure(AppError.Validation(ValidationField.WeeklyTargetInvalid))
        }
        if (maxMemberCap < MIN_MEMBER_CAP || maxMemberCap > MAX_MEMBER_CAP) {
            return Result.failure(AppError.Validation(ValidationField.MemberCapTooSmall))
        }
        // Lowering the cap below the current headcount would leave the group
        // over its own limit. Nobody is silently removed to make it fit.
        if (maxMemberCap < group.memberCount) {
            return Result.failure(
                AppError.Validation(ValidationField.MemberCapBelowMemberCount)
            )
        }

        return groupRepository.updateGroupSettings(
            groupId = group.groupId,
            name = trimmed,
            weeklyTarget = weeklyTarget,
            maxMemberCap = maxMemberCap,
            canMembersEditTarget = canMembersEditTarget,
            weekStartDay = weekStartDay.name,
        )
    }

    companion object {
        const val MIN_MEMBER_CAP = 2
        const val MAX_MEMBER_CAP = 20
        const val MAX_WEEKLY_TARGET = 1_000_000
        const val MAX_GROUP_NAME_LENGTH = 100
    }
}

/**
 * Removes a member, as the admin.
 *
 * The admin cannot remove themselves through this path. Leaving is a different
 * action with a different consequence, since the group would need a new admin,
 * and quietly treating "remove me" as "hand over and leave" from a member list
 * would be a surprising amount to infer from one tap.
 */
class RemoveGroupMemberUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(
        group: Group,
        editorId: String,
        memberId: String,
    ): Result<Unit> {
        if (group.adminId != editorId) {
            return Result.failure(AppError.PermissionDenied())
        }
        if (memberId == group.adminId) {
            return Result.failure(AppError.PermissionDenied())
        }
        if (memberId.isBlank()) {
            return Result.failure(AppError.Validation(ValidationField.DisplayNameMissing))
        }
        return groupRepository.removeMember(group.groupId, memberId)
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