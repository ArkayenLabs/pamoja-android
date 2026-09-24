package com.pamoja.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the shared-goal promise independently of any screen. */
class StepGoalTest {

    @Test
    fun `approved presets are stable weekly group totals`() {
        assertEquals(
            listOf(35_000, 50_000, 70_000, 100_000, 150_000),
            StepGoal.PRESETS_WEEKLY_TOTAL,
        )
        assertEquals(70_000, StepGoal.DEFAULT_WEEKLY_TOTAL)
        assertEquals(10_000, StepGoal.MIN_SELECTABLE_WEEKLY_TOTAL)
        assertEquals(2_800_000, StepGoal.MAX_SELECTABLE_WEEKLY_TOTAL)
    }

    @Test
    fun `presets and deliberate custom shared totals are selectable`() {
        StepGoal.PRESETS_WEEKLY_TOTAL.forEach {
            assertTrue(StepGoal.isSelectableWeeklyTotal(it))
            assertTrue(StepGoal.isPresetWeeklyTotal(it))
        }
        assertTrue(StepGoal.isSelectableWeeklyTotal(300_000))
        assertFalse(StepGoal.isPresetWeeklyTotal(300_000))
        assertFalse(StepGoal.isSelectableWeeklyTotal(9_999))
        assertFalse(StepGoal.isSelectableWeeklyTotal(2_800_001))
    }

    @Test
    fun `changing member count cannot change a shared target`() {
        val selectedTarget = StepGoal.DEFAULT_WEEKLY_TOTAL

        val groupAtTwoMembers = Group(
            weeklyTarget = selectedTarget,
            memberCount = 2,
            maxMemberCap = 2,
        )
        val groupAtTwentyMembers = groupAtTwoMembers.copy(
            memberCount = 20,
            maxMemberCap = 20,
        )

        assertEquals(selectedTarget, groupAtTwoMembers.weeklyTarget)
        assertEquals(selectedTarget, groupAtTwentyMembers.weeklyTarget)
    }

    @Test
    fun `new groups carry no retired per-person metadata`() {
        val group = Group()

        assertEquals(0, group.dailyPerPersonTarget)
        assertFalse(group.hasRetiredPerPersonSetting)
    }

    @Test
    fun `older non-standard target is preserved as an explicit edit option`() {
        val olderTarget = 1_120_000
        val options = StepGoal.editOptionsFor(olderTarget)

        assertTrue(olderTarget in options)
        assertTrue(StepGoal.DEFAULT_WEEKLY_TOTAL in options)
        assertEquals(options.sorted(), options)
        assertEquals(options.distinct(), options)
    }

    @Test
    fun `invalid stored target is never offered as an edit option`() {
        val options = StepGoal.editOptionsFor(StepGoal.MAX_WEEKLY_TOTAL + 1)

        assertEquals(StepGoal.PRESETS_WEEKLY_TOTAL, options)
    }

    @Test
    fun `storage accepts older totals but rejects zero and overflow`() {
        assertTrue(StepGoal.isValidWeeklyTotal(1_120_000))
        assertTrue(StepGoal.isValidWeeklyTotal(StepGoal.MAX_WEEKLY_TOTAL))
        assertFalse(StepGoal.isValidWeeklyTotal(9_999))
        assertFalse(StepGoal.isValidWeeklyTotal(0))
        assertFalse(StepGoal.isValidWeeklyTotal(-1))
        assertFalse(StepGoal.isValidWeeklyTotal(StepGoal.MAX_WEEKLY_TOTAL + 1))
    }
}
