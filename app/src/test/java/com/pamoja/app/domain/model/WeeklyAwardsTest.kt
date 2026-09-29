package com.pamoja.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyAwardsTest {

    private fun member(id: String, steps: Long, mask: Int?) = AwardCandidate(id, steps, mask)

    @Test
    fun theTopWalkerGetsMostStepsAndOnlyOneAwardEach() {
        val awards = WeeklyAwards.of(
            listOf(
                member("priya", 17_400, 0b1111),
                member("mom", 9_700, 0b1111),
                member("arjun", 7_000, 0b0001),
            ),
            elapsed = 4,
        )

        assertEquals(listOf("priya" to WeeklyAward.MOST_STEPS, "mom" to WeeklyAward.EVERY_DAY), awards)
    }

    @Test
    fun aTieForMostStepsHasNoWinner() {
        val awards = WeeklyAwards.of(listOf(member("a", 5_000, null), member("b", 5_000, null)), elapsed = 3)

        assertTrue(awards.isEmpty())
    }

    @Test
    fun todayDoesNotCountAgainstWalkingEveryDay() {
        // Three days finished, all walked; today not yet.
        val awards = WeeklyAwards.of(listOf(member("top", 20_000, 0), member("dad", 3_000, 0b0111)), elapsed = 4)

        assertEquals("dad" to WeeklyAward.EVERY_DAY, awards[1])
    }

    @Test
    fun oneOrTwoDaysInIsTooSoonForEveryDay() {
        val awards = WeeklyAwards.of(listOf(member("top", 20_000, 0), member("dad", 3_000, 0b011)), elapsed = 2)

        assertEquals(1, awards.size)
    }

    @Test
    fun aComebackIsAMissedDayThenTheTwoLatestWalked() {
        // Walked Mon, missed Tue and Wed, walked Thu and today (Fri).
        val awards = WeeklyAwards.of(listOf(member("top", 20_000, 0), member("dad", 5_000, 0b11001)), elapsed = 5)

        assertEquals("dad" to WeeklyAward.COMEBACK, awards[1])
    }

    @Test
    fun walkingEveryDayIsNotAComeback() {
        val awards = WeeklyAwards.of(listOf(member("top", 20_000, 0), member("mom", 5_000, 0b11111)), elapsed = 5)

        assertEquals("mom" to WeeklyAward.EVERY_DAY, awards[1])
    }

    @Test
    fun unknownDaysEarnNothingButDoNotBreakAnything() {
        val awards = WeeklyAwards.of(listOf(member("old-app", 30_000, null), member("b", 10_000, null)), elapsed = 7)

        assertEquals(listOf("old-app" to WeeklyAward.MOST_STEPS), awards)
    }

    @Test
    fun neverMoreThanThreeFaces() {
        val everyone = (1..6).map { member("m$it", it * 1_000L, 0b1111111) }

        assertEquals(WeeklyAwards.MAX_FACES, WeeklyAwards.of(everyone, elapsed = 7).size)
    }

    @Test
    fun walksCountOnlyDaysThatHaveBegun() {
        val members = listOf(member("a", 0, 0b1111111), member("b", 0, 0b0000101), member("c", 0, null))

        assertEquals(3 + 2, WeeklyAwards.walks(members, elapsed = 3))
    }

    @Test
    fun walkedDaysMaskIsBuiltFromTheWeeksEntriesOnly() {
        val monday = LocalDate.of(2026, 9, 28)
        val entries = listOf(
            StepEntry("u", 5_000, "2026-09-27"), // the Sunday before
            StepEntry("u", 5_000, "2026-09-28"),
            StepEntry("u", 999, "2026-09-29"), // not a walk
            StepEntry("u", 1_000, "2026-09-30"),
            StepEntry("u", 8_000, "2026-10-05"), // the Monday after
        )

        assertEquals(0b101, WalkedDays.of(entries, monday))
    }

    @Test
    fun aFinishedWeekNeedsAllSevenDaysForEveryDay() {
        val sixDays = WeeklyAwards.of(listOf(member("top", 20_000, 0), member("dad", 5_000, 0b0111111)), elapsed = 7, weekOver = true)
        val seven = WeeklyAwards.of(listOf(member("top", 20_000, 0), member("dad", 5_000, 0b1111111)), elapsed = 7, weekOver = true)

        assertEquals(1, sixDays.size)
        assertEquals("dad" to WeeklyAward.EVERY_DAY, seven[1])
    }
}
