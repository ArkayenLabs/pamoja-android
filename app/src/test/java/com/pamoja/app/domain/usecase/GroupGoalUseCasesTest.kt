package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class GroupGoalUseCasesTest {
    @Test
    fun `leaving removes only the authenticated member and refuses the organizer`() = runTest {
        val repository = RecordingGroupRepository()
        val auth = com.pamoja.app.testing.SponsorshipAuthRepository(User(userId = "member"))
        val leave = LeaveGroupUseCase(repository, GetCurrentUserUseCase(auth))
        val group = Group(groupId = "group", adminId = "organizer")
        assertTrue(leave(group).isSuccess)
        assertEquals("group" to "member", repository.removedMember)
        repository.removedMember = null
        auth.currentUser = User(userId = "organizer")
        assertTrue(leave(group).exceptionOrNull() is AppError.PermissionDenied)
        assertNull(repository.removedMember)
        auth.currentUser = null
        assertTrue(leave(group).exceptionOrNull() is AppError.SessionExpired)
        assertNull(repository.removedMember)
    }

    @Test
    fun `creating a group stores the selected weekly total regardless of capacity`() = runTest {
        val smallRepository = RecordingGroupRepository()
        val largeRepository = RecordingGroupRepository()

        CreateGroupUseCase(smallRepository)(
            name = "Small family",
            adminId = "admin",
            weeklyTarget = 70_000,
            maxMemberCap = 3,
            weekStartDay = DayOfWeek.MONDAY,
        ).getOrThrow()
        CreateGroupUseCase(largeRepository)(
            name = "Large family",
            adminId = "admin",
            weeklyTarget = 70_000,
            maxMemberCap = 20,
            weekStartDay = DayOfWeek.MONDAY,
        ).getOrThrow()

        assertEquals(70_000, smallRepository.createdGroup?.weeklyTarget)
        assertEquals(70_000, largeRepository.createdGroup?.weeklyTarget)
        assertEquals(0, smallRepository.createdGroup?.dailyPerPersonTarget)
        assertEquals(0, largeRepository.createdGroup?.dailyPerPersonTarget)
    }

    @Test
    fun `creating a group accepts a custom weekly total`() = runTest {
        val repository = RecordingGroupRepository()

        val result = CreateGroupUseCase(repository)(
            name = "Family",
            adminId = "admin",
            weeklyTarget = 300_000,
            maxMemberCap = 6,
            weekStartDay = DayOfWeek.MONDAY,
        )

        assertTrue(result.isSuccess)
        assertEquals(300_000, repository.createdGroup?.weeklyTarget)
        assertFalse(repository.createdGroup?.canMembersEditTarget ?: true)
    }

    @Test
    fun `same admin cannot create a case variant of an existing group name`() = runTest {
        val repository = RecordingGroupRepository().apply {
            userGroups = listOf(
                Group(groupId = "one", name = "Together", adminId = "admin")
            )
        }

        val result = CreateGroupUseCase(repository)(
            name = "  together  ",
            adminId = "admin",
            weeklyTarget = 70_000,
            maxMemberCap = 6,
        )

        assertTrue(result.isFailure)
        assertNull(repository.createdGroup)
    }

    @Test
    fun `same group name owned by another admin remains allowed`() = runTest {
        val repository = RecordingGroupRepository().apply {
            userGroups = listOf(
                Group(groupId = "joined", name = "Together", adminId = "someone-else")
            )
        }

        val result = CreateGroupUseCase(repository)(
            name = "Together",
            adminId = "admin",
            weeklyTarget = 70_000,
            maxMemberCap = 6,
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `creating rejects a group name over forty characters`() = runTest {
        val repository = RecordingGroupRepository()

        val result = CreateGroupUseCase(repository)(
            name = "x".repeat(41),
            adminId = "admin",
            weeklyTarget = 70_000,
            maxMemberCap = 6,
        )

        assertEquals(
            ValidationField.GroupNameTooLong,
            (result.exceptionOrNull() as AppError.Validation).field,
        )
        assertNull(repository.createdGroup)
    }

    @Test
    fun `changing capacity preserves an older nonstandard shared goal`() = runTest {
        val repository = RecordingGroupRepository()
        val group = Group(
            groupId = "older-group",
            name = "Older family",
            adminId = "admin",
            weeklyTarget = 1_120_000,
            dailyPerPersonTarget = 8_000,
            maxMemberCap = 20,
            memberCount = 6,
        )

        val result = UpdateGroupSettingsUseCase(repository)(
            group = group,
            editorId = "admin",
            name = "Older family",
            weeklyTarget = group.weeklyTarget,
            maxMemberCap = 10,
            weekStartDay = DayOfWeek.MONDAY,
        )

        assertTrue(result.isSuccess)
        assertEquals(1_120_000, repository.settingsWrite?.weeklyTarget)
        assertEquals(10, repository.settingsWrite?.maxMemberCap)
    }

    @Test
    fun `editing a group accepts a custom total and retires member editing`() = runTest {
        val repository = RecordingGroupRepository()
        val group = Group(
            groupId = "group",
            name = "Family",
            adminId = "admin",
            weeklyTarget = 70_000,
            maxMemberCap = 6,
            memberCount = 4,
            canMembersEditTarget = true,
        )

        val result = UpdateGroupSettingsUseCase(repository)(
            group = group,
            editorId = "admin",
            name = "Family",
            weeklyTarget = 300_000,
            maxMemberCap = 6,
            weekStartDay = DayOfWeek.MONDAY,
        )

        assertTrue(result.isSuccess)
        assertEquals(300_000, repository.settingsWrite?.weeklyTarget)
        assertFalse(repository.settingsWrite?.canMembersEditTarget ?: true)
    }

    @Test
    fun `editing cannot reuse another group name owned by the same admin`() = runTest {
        val repository = RecordingGroupRepository().apply {
            userGroups = listOf(
                Group(groupId = "one", name = "Family", adminId = "admin"),
                Group(groupId = "two", name = "Weekend Crew", adminId = "admin"),
            )
        }
        val group = repository.userGroups.first()

        val result = UpdateGroupSettingsUseCase(repository)(
            group = group,
            editorId = "admin",
            name = " weekend   crew ",
            weeklyTarget = group.weeklyTarget,
            maxMemberCap = group.maxMemberCap,
            weekStartDay = DayOfWeek.MONDAY,
        )

        assertTrue(result.isFailure)
        assertNull(repository.settingsWrite)
    }

    @Test
    fun `editing rejects a group name over forty characters`() = runTest {
        val repository = RecordingGroupRepository()
        val group = Group(
            groupId = "group",
            name = "Family",
            adminId = "admin",
            weeklyTarget = 70_000,
            maxMemberCap = 6,
            memberCount = 2,
        )

        val result = UpdateGroupSettingsUseCase(repository)(
            group = group,
            editorId = "admin",
            name = "x".repeat(41),
            weeklyTarget = group.weeklyTarget,
            maxMemberCap = group.maxMemberCap,
            weekStartDay = DayOfWeek.MONDAY,
        )

        assertEquals(
            ValidationField.GroupNameTooLong,
            (result.exceptionOrNull() as AppError.Validation).field,
        )
        assertNull(repository.settingsWrite)
    }

    @Test
    fun `group admin can request permanent deletion`() = runTest {
        val repository = RecordingGroupRepository()
        val group = Group(
            groupId = "6413795d-42d0-4f2b-80df-f017a9d32817",
            name = "Family",
            adminId = "admin",
        )

        val result = DeleteGroupUseCase(repository)(group, editorId = "admin")

        assertTrue(result.isSuccess)
        assertEquals(group.groupId, repository.deletedGroupId)
    }

    @Test
    fun `non-admin cannot request permanent deletion`() = runTest {
        val repository = RecordingGroupRepository()
        val group = Group(
            groupId = "6413795d-42d0-4f2b-80df-f017a9d32817",
            name = "Family",
            adminId = "admin",
        )

        val result = DeleteGroupUseCase(repository)(group, editorId = "member")

        assertTrue(result.isFailure)
        assertNull(repository.deletedGroupId)
    }

    private data class SettingsWrite(
        val weeklyTarget: Int,
        val maxMemberCap: Int,
        val canMembersEditTarget: Boolean,
    )

    private class RecordingGroupRepository : GroupRepository {
        var removedMember: Pair<String, String>? = null
        var createdGroup: Group? = null
        var settingsWrite: SettingsWrite? = null
        var deletedGroupId: String? = null
        var userGroups: List<Group> = emptyList()

        override suspend fun createGroup(group: Group): Result<Group> {
            createdGroup = group
            return Result.success(group)
        }

        override suspend fun updateGroupSettings(
            groupId: String,
            name: String,
            weeklyTarget: Int,
            maxMemberCap: Int,
            canMembersEditTarget: Boolean,
            weekStartDay: String,
        ): Result<Unit> {
            settingsWrite = SettingsWrite(weeklyTarget, maxMemberCap, canMembersEditTarget)
            return Result.success(Unit)
        }

        override suspend fun getGroup(groupId: String): Result<Group> = unexpected()
        override suspend fun updateGroup(group: Group): Result<Unit> = unexpected()
        override suspend fun getGroupByInviteLink(inviteLink: String): Result<Group> = unexpected()
        override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> = unexpected()
        override fun getGroupMembers(groupId: String): Flow<List<User>> = emptyFlow()
        override fun getGroupMemberships(groupId: String): Flow<List<Membership>> = emptyFlow()
        override suspend fun publishMyWeeklySteps(
            groupId: String,
            userId: String,
            steps: Long,
            weekStart: String,
            todaySteps: Long,
            todayDate: String,
        ): Result<Unit> = unexpected()
        override suspend fun getMembership(userId: String, groupId: String): Result<Membership> =
            unexpected()
        override fun getUserGroups(userId: String): Flow<List<Group>> = flowOf(userGroups)
        override suspend fun updateMemberCap(groupId: String, cap: Int): Result<Unit> = unexpected()
        override suspend fun updateWeeklyTarget(groupId: String, target: Int): Result<Unit> = unexpected()
        override suspend fun deactivateInviteLink(groupId: String): Result<Unit> = unexpected()
        override suspend fun removeMember(groupId: String, userId: String): Result<Unit> {
            removedMember = groupId to userId
            return Result.success(Unit)
        }
        override suspend fun deleteGroup(groupId: String): Result<Unit> {
            deletedGroupId = groupId
            return Result.success(Unit)
        }
        override suspend fun updateGroupPhoto(groupId: String, photoUrl: String): Result<Unit> =
            unexpected()
        override suspend fun publishWeeklyTotal(
            groupId: String,
            weeklySteps: Long,
            weekStart: String,
        ): Result<Unit> = unexpected()

        private fun unexpected(): Nothing = error("Unexpected repository call")
    }
}
