package com.pamoja.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StepGoal], the goal maths.
 *
 * Pure Kotlin, so these are real JVM unit tests rather than emulator-backed
 * ones. They exist mostly to pin the two things that were actually wrong before
 * this type existed: that the goal did not scale with the group, and that the
 * ceilings disagreed across four layers.
 */
class StepGoalTest {

    @Test
    fun `weekly total is per person times members times seven`() {
        assertEquals(8_000 * 5 * 7, StepGoal.weeklyTotalFor(8_000, 5))
    }

    /**
     * The regression that matters. The old fixed total meant a bigger group had
     * an easier goal, and since the member cap is what premium sells, paying
     * made the goal easier. Same per-person figure must mean the same effort at
     * any group size.
     */
    @Test
    fun `a bigger group does not get an easier goal`() {
        val small = StepGoal.weeklyTotalFor(8_000, 2)
        val atFreeCap = StepGoal.weeklyTotalFor(8_000, 8)
        val atPremiumCap = StepGoal.weeklyTotalFor(8_000, 20)

        // Per person per day is identical in all three, which is the point.
        assertEquals(8_000, StepGoal.dailyPerPersonFor(small, 2))
        assertEquals(8_000, StepGoal.dailyPerPersonFor(atFreeCap, 8))
        assertEquals(8_000, StepGoal.dailyPerPersonFor(atPremiumCap, 20))

        // And the group total genuinely rises with the group.
        assertTrue(atPremiumCap > atFreeCap)
        assertTrue(atFreeCap > small)
    }

    /**
     * The blocker found while planning: the recommended default at the premium
     * cap is 1,120,000, which the old `firestore.rules` ceiling of 1,000,000
     * would have rejected. The default must be storable at every legal group
     * size or the model cannot ship.
     */
    @Test
    fun `the default is storable at the largest allowed group`() {
        val atCap = StepGoal.weeklyTotalFor(
            StepGoal.DEFAULT_DAILY_PER_PERSON,
            StepGoal.MAX_GROUP_MEMBERS,
        )

        assertEquals(1_120_000, atCap)
        assertTrue("The default must fit under the ceiling", StepGoal.isValidWeeklyTotal(atCap))
    }

    /** Nothing the model can produce may exceed the ceiling the rules enforce. */
    @Test
    fun `the worst case is exactly the declared ceiling`() {
        val worst = StepGoal.weeklyTotalFor(
            StepGoal.MAX_DAILY_PER_PERSON,
            StepGoal.MAX_GROUP_MEMBERS,
        )

        assertEquals(StepGoal.MAX_WEEKLY_TOTAL, worst)
        assertTrue(StepGoal.isValidWeeklyTotal(worst))
    }

    @Test
    fun `every preset is storable at every allowed group size`() {
        for (preset in StepGoal.PRESETS_DAILY_PER_PERSON) {
            for (members in 1..StepGoal.MAX_GROUP_MEMBERS) {
                val total = StepGoal.weeklyTotalFor(preset, members)
                assertTrue(
                    "preset $preset at $members members produced $total",
                    StepGoal.isValidWeeklyTotal(total),
                )
            }
        }
    }

    @Test
    fun `presets sit inside the allowed per person range`() {
        for (preset in StepGoal.PRESETS_DAILY_PER_PERSON) {
            assertTrue(preset >= StepGoal.MIN_DAILY_PER_PERSON)
            assertTrue(preset <= StepGoal.MAX_DAILY_PER_PERSON)
        }
        assertTrue(StepGoal.DEFAULT_DAILY_PER_PERSON in StepGoal.PRESETS_DAILY_PER_PERSON)
    }

    /**
     * A zero target reads as "already achieved" everywhere it is compared, so
     * no input may produce one.
     */
    @Test
    fun `an empty or corrupt member count cannot produce a zero target`() {
        assertTrue(StepGoal.weeklyTotalFor(8_000, 0) > 0)
        assertTrue(StepGoal.weeklyTotalFor(8_000, -5) > 0)
    }

    @Test
    fun `an out of range per person figure is clamped rather than trusted`() {
        assertEquals(
            StepGoal.weeklyTotalFor(StepGoal.MAX_DAILY_PER_PERSON, 4),
            StepGoal.weeklyTotalFor(999_999, 4),
        )
        assertEquals(
            StepGoal.weeklyTotalFor(StepGoal.MIN_DAILY_PER_PERSON, 4),
            StepGoal.weeklyTotalFor(1, 4),
        )
    }

    /** Legacy groups keep their absolute target, so it still has to read well. */
    @Test
    fun `an old fixed target reports the per person figure it implies`() {
        // The old default, at the old default cap.
        assertEquals(1_250, StepGoal.dailyPerPersonFor(70_000, 8))
        assertEquals(500, StepGoal.dailyPerPersonFor(70_000, 20))
    }

    @Test
    fun `a nonsense total is rejected`() {
        assertFalse(StepGoal.isValidWeeklyTotal(0))
        assertFalse(StepGoal.isValidWeeklyTotal(-1))
        assertFalse(StepGoal.isValidWeeklyTotal(StepGoal.MAX_WEEKLY_TOTAL + 1))
    }
}
