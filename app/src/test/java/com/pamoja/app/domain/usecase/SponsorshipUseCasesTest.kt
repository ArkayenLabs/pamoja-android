package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.SponsorshipFailure
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.repository.GroupSponsorship
import com.pamoja.app.domain.repository.GroupSponsorshipRepository
import com.pamoja.app.testing.SponsorshipAuthRepository
import com.pamoja.app.testing.SponsorshipGroupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorshipUseCasesTest {

    @Test
    fun `invalid group is refused before the state-changing call`() = runTest {
        val repository = FakeSponsorshipRepository()

        val result = ActivateGroupSponsorshipUseCase(repository)("not-a-group")

        assertFalse(repository.wasCalled)
        val error = result.exceptionOrNull()
        assertTrue(error is AppError.Sponsorship)
        assertEquals(
            SponsorshipFailure.InvalidGroup,
            (error as AppError.Sponsorship).reason,
        )
    }

    @Test
    fun `valid group is normalized and forwarded once`() = runTest {
        val repository = FakeSponsorshipRepository()

        val result = ActivateGroupSponsorshipUseCase(repository)("  $GROUP_ID  ")

        assertTrue(result.isSuccess)
        assertEquals(GROUP_ID, repository.receivedGroupId)
        assertEquals(GROUP_ID, result.getOrThrow().groupId)
    }

    @Test
    fun `purchase confirmation retries only while RevenueCat is catching up`() = runTest {
        val waiting = Result.failure<GroupSponsorship>(
            AppError.Sponsorship(SponsorshipFailure.NoActiveSubscription)
        )
        val success = Result.success(
            GroupSponsorship(GROUP_ID, leaseValidUntilMillis = 1_800_000_000_000L)
        )
        val repository = SequencedSponsorshipRepository(
            mutableListOf(waiting, waiting, success)
        )
        val useCase = ConfirmPurchasedGroupAccessUseCase(
            ActivateGroupSponsorshipUseCase(repository)
        )

        val result = useCase(GROUP_ID)

        assertTrue(result.isSuccess)
        assertEquals(3, repository.calls)
    }

    @Test
    fun `purchase confirmation never retries an ownership conflict`() = runTest {
        val conflict = Result.failure<GroupSponsorship>(
            AppError.Sponsorship(SponsorshipFailure.SubscriptionAssignedElsewhere)
        )
        val repository = SequencedSponsorshipRepository(mutableListOf(conflict))
        val useCase = ConfirmPurchasedGroupAccessUseCase(
            ActivateGroupSponsorshipUseCase(repository)
        )

        val result = useCase(GROUP_ID)

        assertSponsorshipFailure(result, SponsorshipFailure.SubscriptionAssignedElsewhere)
        assertEquals(1, repository.calls)
    }

    @Test
    fun `current member resolves the exact server backed group`() = runTest {
        val group = Group(groupId = GROUP_ID, name = "Asha's Sunrise Walkers")
        val authRepository = SponsorshipAuthRepository()
        val groupRepository = SponsorshipGroupRepository(
            groupResult = Result.success(group),
            membershipResult = Result.success(
                Membership(userId = MEMBER_ID, groupId = GROUP_ID),
            ),
        )

        val result = ResolveSponsorshipGroupUseCase(
            authRepository = authRepository,
            groupRepository = groupRepository,
        )(GROUP_ID)

        assertEquals(group, result.getOrThrow())
        assertEquals(1, groupRepository.groupReads)
        assertEquals(1, groupRepository.membershipReads)
    }

    @Test
    fun `invalid route id is blocked before user or group reads`() = runTest {
        val authRepository = SponsorshipAuthRepository()
        val groupRepository = SponsorshipGroupRepository(
            groupResult = Result.success(Group(groupId = GROUP_ID, name = "Walkers")),
            membershipResult = Result.success(
                Membership(userId = MEMBER_ID, groupId = GROUP_ID),
            ),
        )

        val result = ResolveSponsorshipGroupUseCase(
            authRepository = authRepository,
            groupRepository = groupRepository,
        )("not-a-group")

        assertSponsorshipFailure(result, SponsorshipFailure.InvalidGroup)
        assertEquals(0, authRepository.currentUserReads)
        assertEquals(0, groupRepository.groupReads)
        assertEquals(0, groupRepository.membershipReads)
    }

    @Test
    fun `missing membership cannot reach a group paywall`() = runTest {
        val groupRepository = SponsorshipGroupRepository(
            groupResult = Result.success(Group(groupId = GROUP_ID, name = "Walkers")),
            membershipResult = Result.failure(AppError.NotFound()),
        )

        val result = ResolveSponsorshipGroupUseCase(
            authRepository = SponsorshipAuthRepository(),
            groupRepository = groupRepository,
        )(GROUP_ID)

        assertSponsorshipFailure(result, SponsorshipFailure.NotCurrentMember)
    }

    @Test
    fun `unavailable group is a safe product outcome`() = runTest {
        val groupRepository = SponsorshipGroupRepository(
            groupResult = Result.failure(AppError.PermissionDenied()),
            membershipResult = Result.success(
                Membership(userId = MEMBER_ID, groupId = GROUP_ID),
            ),
        )

        val result = ResolveSponsorshipGroupUseCase(
            authRepository = SponsorshipAuthRepository(),
            groupRepository = groupRepository,
        )(GROUP_ID)

        assertSponsorshipFailure(result, SponsorshipFailure.GroupUnavailable)
        assertEquals(0, groupRepository.membershipReads)
    }

    private fun assertSponsorshipFailure(
        result: Result<*>,
        expected: SponsorshipFailure,
    ) {
        val error = result.exceptionOrNull()
        assertTrue(error is AppError.Sponsorship)
        assertEquals(expected, (error as AppError.Sponsorship).reason)
    }

    private class FakeSponsorshipRepository : GroupSponsorshipRepository {
        var receivedGroupId: String? = null
        val wasCalled: Boolean get() = receivedGroupId != null

        override suspend fun activate(
            groupId: String,
            replaceExisting: Boolean,
        ): Result<GroupSponsorship> {
            receivedGroupId = groupId
            return Result.success(
                GroupSponsorship(
                    groupId = groupId,
                    leaseValidUntilMillis = 1_800_000_000_000L,
                )
            )
        }
    }

    private class SequencedSponsorshipRepository(
        private val results: MutableList<Result<GroupSponsorship>>,
    ) : GroupSponsorshipRepository {
        var calls: Int = 0

        override suspend fun activate(
            groupId: String,
            replaceExisting: Boolean,
        ): Result<GroupSponsorship> {
            calls += 1
            return if (results.size > 1) results.removeAt(0) else results.first()
        }
    }

    private companion object {
        const val GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817"
        const val MEMBER_ID = "member-1"
    }
}
