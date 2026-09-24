package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.GroupAccess
import com.pamoja.app.domain.model.GroupAccessSnapshot
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupFeature
import com.pamoja.app.domain.model.GroupPreview
import com.pamoja.app.domain.repository.GroupAccessRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class GroupAccessUseCaseTest {

    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)

    @Test
    fun `active circle lease grants premium after explicit loading`() = runTest {
        val paid = access(validUntil = NOW + 60_000L)
        val states = useCase(snapshot(premiumAccess = paid))(GROUP_ID, USER_ID)
            .take(2)
            .toList()

        assertEquals(GroupAccessState.Loading, states[0])
        assertEquals(GroupAccessState.Premium(paid), states[1])
        assertTrue(states[1].isPremium)
        // Old paid leases only contain circle_v1. They still receive every new
        // Premium capability without a repurchase or client-side migration.
        assertTrue(states[1].hasNextWeekTogether)
    }

    @Test
    fun `expired server lease is free even when access document says premium`() = runTest {
        val states = useCase(snapshot(premiumAccess = access(validUntil = NOW)))(GROUP_ID, USER_ID)
            .take(2)
            .toList()

        assertEquals(GroupAccessState.Free, states.last())
        assertFalse(states.last().isPremium)
        assertFalse(states.last().hasNextWeekTogether)
    }

    @Test
    fun `active preview grants circle without claiming premium`() = runTest {
        val preview = preview(validUntil = NOW + 60_000L)
        val states = useCase(snapshot(preview = preview))(GROUP_ID, USER_ID)
            .take(2)
            .toList()

        assertEquals(GroupAccessState.Preview(preview), states.last())
        assertTrue(states.last().hasCircleAccess)
        assertFalse(states.last().isPremium)
        assertFalse(states.last().hasNextWeekTogether)
    }

    @Test
    fun `ended preview remains explicit instead of looking like a new free group`() = runTest {
        val ended = preview(validUntil = NOW)
        val states = useCase(snapshot(preview = ended))(GROUP_ID, USER_ID)
            .take(2)
            .toList()

        assertEquals(GroupAccessState.PreviewExpired(ended), states.last())
        assertFalse(states.last().hasCircleAccess)
    }

    @Test
    fun `paid access takes priority while preview is also active`() = runTest {
        val paid = access(validUntil = NOW + 60_000L)
        val states = useCase(
            snapshot(
                premiumAccess = paid,
                preview = preview(validUntil = NOW + 120_000L),
            ),
        )(GROUP_ID, USER_ID).take(2).toList()

        assertEquals(GroupAccessState.Premium(paid), states.last())
    }

    @Test
    fun `paid expiry falls back to the remaining group preview`() = runTest {
        val paid = access(validUntil = NOW + 1_000L)
        val remainingPreview = preview(validUntil = NOW + 2_000L)
        val collected = async {
            useCase(snapshot(premiumAccess = paid, preview = remainingPreview))(GROUP_ID, USER_ID)
                .take(3)
                .toList()
        }

        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(
            listOf(
                GroupAccessState.Loading,
                GroupAccessState.Premium(paid),
                GroupAccessState.Preview(remainingPreview),
            ),
            collected.await(),
        )
    }

    @Test
    fun `missing access documents are a normal free group`() = runTest {
        val states = useCase(snapshot())(GROUP_ID, USER_ID).take(2).toList()

        assertEquals(listOf(GroupAccessState.Loading, GroupAccessState.Free), states)
    }

    @Test
    fun `unavailable server never falls back to paid or preview`() = runTest {
        val failure = AppError.Offline()
        val states = useCase(Result.failure(failure))(GROUP_ID, USER_ID)
            .take(2)
            .toList()

        val unavailable = states.last() as GroupAccessState.Unavailable
        assertEquals(failure, unavailable.error)
        assertFalse(unavailable.hasCircleAccess)
    }

    @Test
    fun `active paid access expires locally at server valid until time`() = runTest {
        val paid = access(validUntil = NOW + 1_000L)
        val collected = async {
            useCase(snapshot(premiumAccess = paid))(GROUP_ID, USER_ID)
                .take(3)
                .toList()
        }

        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(
            listOf(
                GroupAccessState.Loading,
                GroupAccessState.Premium(paid),
                GroupAccessState.Free,
            ),
            collected.await(),
        )
    }

    @Test
    fun `active preview expires locally into the ended state`() = runTest {
        val activePreview = preview(validUntil = NOW + 1_000L)
        val collected = async {
            useCase(snapshot(preview = activePreview))(GROUP_ID, USER_ID)
                .take(3)
                .toList()
        }

        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(
            listOf(
                GroupAccessState.Loading,
                GroupAccessState.Preview(activePreview),
                GroupAccessState.PreviewExpired(activePreview),
            ),
            collected.await(),
        )
    }

    @Test
    fun `unsupported feature set does not grant this app premium`() = runTest {
        val unsupported = GroupAccess(
            groupId = GROUP_ID,
            featureSet = emptySet(),
            validUntilMillis = NOW + 60_000L,
        )

        val states = useCase(snapshot(premiumAccess = unsupported))(GROUP_ID, USER_ID)
            .take(2)
            .toList()

        assertEquals(GroupAccessState.Free, states.last())
    }

    private fun useCase(result: Result<GroupAccessSnapshot>) = ObserveGroupAccessUseCase(
        repository = FixedGroupAccessRepository(result),
        clock = clock,
    )

    private fun snapshot(
        premiumAccess: GroupAccess? = null,
        preview: GroupPreview? = null,
    ) = Result.success(
        GroupAccessSnapshot(
            premiumAccess = premiumAccess,
            preview = preview,
        ),
    )

    private fun access(validUntil: Long) = GroupAccess(
        groupId = GROUP_ID,
        featureSet = setOf(GroupFeature.CircleV1),
        validUntilMillis = validUntil,
    )

    private fun preview(validUntil: Long) = GroupPreview(
        groupId = GROUP_ID,
        featureSet = setOf(GroupFeature.CircleV1),
        eligibleWeekStart = "2027-01-11",
        startedAtMillis = NOW - 1_000L,
        validUntilMillis = validUntil,
    )

    private class FixedGroupAccessRepository(
        private val result: Result<GroupAccessSnapshot>,
    ) : GroupAccessRepository {
        override fun observeForCurrentMember(
            groupId: String,
            userId: String,
        ): Flow<Result<GroupAccessSnapshot>> = flowOf(result)
    }

    private companion object {
        const val GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817"
        const val USER_ID = "member-1"
        const val NOW = 1_800_000_000_000L
    }
}
