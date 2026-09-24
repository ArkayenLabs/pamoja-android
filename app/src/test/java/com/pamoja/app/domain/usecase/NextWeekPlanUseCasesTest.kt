package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.NextWeekPlanChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class NextWeekPlanUseCasesTest {

    private class RecordingPlans : com.pamoja.app.domain.repository.NextWeekPlanRepository {
        var saved = false
        var savedBasis: com.pamoja.app.domain.model.GroupWeekSummary? = null
        var savedTarget = 0
        override fun observePlan(groupId: String, weekStart: String) =
            kotlinx.coroutines.flow.flowOf(Result.success<com.pamoja.app.domain.model.NextWeekPlan?>(null))
        override fun observeMyResponse(groupId: String, weekStart: String, userId: String) =
            kotlinx.coroutines.flow.flowOf(Result.success<com.pamoja.app.domain.model.NextWeekPlanResponse?>(null))
        override suspend fun savePlan(groupId: String, weekStart: String, targetSteps: Int,
            choice: NextWeekPlanChoice, source: com.pamoja.app.domain.model.GroupWeekSummary?,
            userId: String): Result<Unit> {
            saved = true; savedBasis = source; savedTarget = targetSteps
            return Result.success(Unit)
        }
        override suspend fun saveResponse(groupId: String, weekStart: String, userId: String,
            response: com.pamoja.app.domain.model.NextWeekResponse) = Result.success(Unit)
    }

    @Test fun `organizer saves day one without altering current goal`() = kotlinx.coroutines.runBlocking {
        val repository = RecordingPlans()
        val group = Group(groupId = "group", adminId = "organizer", weeklyTarget = 100_000)
        val result = SaveNextWeekPlanUseCase(repository)(group, "organizer", "2026-09-14",
            NextWeekPlanChoice.Gentler, null, null)
        org.junit.Assert.assertTrue(result.isSuccess)
        org.junit.Assert.assertTrue(repository.saved)
        assertNull(repository.savedBasis)
        assertEquals(80_000, repository.savedTarget)
        assertEquals(100_000, group.weeklyTarget)
    }

    @Test fun `member and wrong week boundary cannot save`() = kotlinx.coroutines.runBlocking {
        val repository = RecordingPlans()
        val group = Group(groupId = "group", adminId = "organizer", weeklyTarget = 100_000)
        val save = SaveNextWeekPlanUseCase(repository)
        org.junit.Assert.assertTrue(save(group, "member", "2026-09-14",
            NextWeekPlanChoice.Repeat, null, null).isFailure)
        org.junit.Assert.assertTrue(save(group, "organizer", "2026-09-15",
            NextWeekPlanChoice.Repeat, null, null).isFailure)
        org.junit.Assert.assertFalse(repository.saved)
    }

    @Test
    fun `repeat preserves the current shared target`() {
        assertEquals(
            70_000,
            targetForChoice(70_000, NextWeekPlanChoice.Repeat, null),
        )
    }

    @Test
    fun `gentler lowers twenty percent and rounds to a clear target`() {
        assertEquals(
            120_000,
            targetForChoice(150_000, NextWeekPlanChoice.Gentler, null),
        )
        assertEquals(
            10_000,
            targetForChoice(10_000, NextWeekPlanChoice.Gentler, null),
        )
    }

    @Test
    fun `custom rejects targets outside the existing group bounds`() {
        assertEquals(
            90_000,
            targetForChoice(70_000, NextWeekPlanChoice.Custom, 90_000),
        )
        assertNull(targetForChoice(70_000, NextWeekPlanChoice.Custom, 9_999))
        assertNull(targetForChoice(70_000, NextWeekPlanChoice.Custom, null))
    }

    @Test
    fun `next week follows the group's stored boundary rather than device date`() {
        val group = Group(weekStart = "2026-09-07", weekStartDay = "MONDAY")
        val clock = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC)

        assertEquals("2026-09-14", upcomingWeekStart(group, clock))
    }

    @Test
    fun `stale cached week never creates a plan in the past`() {
        val group = Group(weekStart = "2026-08-03", weekStartDay = "MONDAY")
        val clock = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC)

        assertEquals("2026-09-14", upcomingWeekStart(group, clock))
    }
}
