package com.pamoja.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class WeeklyPaceTest {

    @Test
    fun `meeting the target is reached whatever the day`() {
        assertEquals(WeeklyPace.Reached, pace(steps = 70_000, elapsed = 2.5, daysLeft = 5))
        assertEquals(WeeklyPace.Reached, pace(steps = 90_000, elapsed = 6.9, daysLeft = 1))
    }

    @Test
    fun `the first day never projects from one morning`() {
        // 7,000 by 10 am on day one would "finish" absurdly early.
        assertEquals(WeeklyPace.PerDay(9_000), pace(steps = 7_000, elapsed = 0.4, daysLeft = 7))
    }

    @Test
    fun `a steady rate projects the finish day`() {
        // 25,000 over 2.5 days is 10,000 a day: 70,000 lands on day seven.
        assertEquals(
            WeeklyPace.OnPace(DayOfWeek.SUNDAY, isToday = false),
            pace(steps = 25_000, elapsed = 2.5, daysLeft = 5),
        )
        // 14,000 a day lands on day five, Friday, for a Monday week.
        assertEquals(
            WeeklyPace.OnPace(DayOfWeek.FRIDAY, isToday = false),
            pace(steps = 35_000, elapsed = 2.5, daysLeft = 5),
        )
    }

    @Test
    fun `the finish day follows the group's own week start`() {
        assertEquals(
            WeeklyPace.OnPace(DayOfWeek.THURSDAY, isToday = false),
            pace(steps = 35_000, elapsed = 2.5, daysLeft = 5, startDay = DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun `finishing today says so`() {
        // Friday evening on a Monday week, 1,000 short at a fast rate.
        assertEquals(
            WeeklyPace.OnPace(DayOfWeek.FRIDAY, isToday = true),
            pace(steps = 69_000, elapsed = 4.8, daysLeft = 3),
        )
    }

    @Test
    fun `behind the pace asks for a daily figure, rounded up`() {
        // 20,000 over 4.5 days will not reach 70,000; 50,000 over 3 days left.
        assertEquals(WeeklyPace.PerDay(16_667), pace(steps = 20_000, elapsed = 4.5, daysLeft = 3))
    }

    @Test
    fun `nothing walked yet is a daily figure, not a projection`() {
        assertEquals(WeeklyPace.PerDay(14_000), pace(steps = 0, elapsed = 2.2, daysLeft = 5))
    }

    @Test
    fun `usual is the median of walked days, rounded to a hundred`() {
        assertEquals(7_500L, UsualSteps.of(listOf(7_480, 0, 9_000, 2_000, 7_520, 0, 8_000, 6_000)))
        assertEquals(8_000L, UsualSteps.of(listOf(8_049, 3_000, 12_000)))
    }

    @Test
    fun `usual needs a few walked days`() {
        assertNull(UsualSteps.of(emptyList()))
        assertNull(UsualSteps.of(listOf(0, 0, 9_000, 8_000, 0)))
    }

    private fun pace(
        steps: Long,
        elapsed: Double,
        daysLeft: Int,
        target: Long = 70_000,
        startDay: DayOfWeek = DayOfWeek.MONDAY,
    ) = WeeklyPace.of(steps, target, startDay, elapsed, daysLeft)
}
