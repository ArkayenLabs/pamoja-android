package com.pamoja.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * `CHECKLIST.md` §2.4 names week-boundary maths as one of exactly three places
 * where a silent bug corrupts real user data, and until now it had no tests at
 * all. These are the first.
 *
 * Every case pins an explicit date rather than using `LocalDate.now()`, so the
 * suite cannot pass on a Tuesday and fail on a Sunday.
 *
 * Reference week: 2026-08-10 is a Monday, 2026-08-16 is the Sunday after it.
 */
class WeekWindowTest {

    private val monday = LocalDate.of(2026, 8, 10)
    private val wednesday = LocalDate.of(2026, 8, 12)
    private val saturday = LocalDate.of(2026, 8, 15)
    private val sunday = LocalDate.of(2026, 8, 16)

    // ── Monday-start groups ──────────────────────────────────────────────

    @Test
    fun `monday start, midweek resolves to that monday`() {
        assertEquals("2026-08-10", WeekWindow.startOf(DayOfWeek.MONDAY, wednesday))
        assertEquals("2026-08-16", WeekWindow.endOf(DayOfWeek.MONDAY, wednesday))
    }

    @Test
    fun `monday start, the monday itself is its own week start`() {
        assertEquals("2026-08-10", WeekWindow.startOf(DayOfWeek.MONDAY, monday))
    }

    @Test
    fun `monday start, sunday is the last day not the next week`() {
        assertEquals("2026-08-10", WeekWindow.startOf(DayOfWeek.MONDAY, sunday))
        assertEquals("2026-08-16", WeekWindow.endOf(DayOfWeek.MONDAY, sunday))
    }

    // ── Sunday-start groups, the case the old hardcoded version got wrong ──

    @Test
    fun `sunday start, the sunday begins a new week rather than ending one`() {
        assertEquals("2026-08-16", WeekWindow.startOf(DayOfWeek.SUNDAY, sunday))
        assertEquals("2026-08-22", WeekWindow.endOf(DayOfWeek.SUNDAY, sunday))
    }

    @Test
    fun `sunday start, saturday closes the week`() {
        assertEquals("2026-08-09", WeekWindow.startOf(DayOfWeek.SUNDAY, saturday))
        assertEquals("2026-08-15", WeekWindow.endOf(DayOfWeek.SUNDAY, saturday))
    }

    @Test
    fun `the same day falls in different weeks depending on the group`() {
        // This is the whole reason the setting is per group and not per user:
        // on this date the two conventions disagree about which week it is.
        val asMonday = WeekWindow.startOf(DayOfWeek.MONDAY, sunday)
        val asSunday = WeekWindow.startOf(DayOfWeek.SUNDAY, sunday)
        assertEquals("2026-08-10", asMonday)
        assertEquals("2026-08-16", asSunday)
    }

    // ── The window is always exactly seven days ──────────────────────────

    @Test
    fun `every start day produces a seven day window`() {
        DayOfWeek.entries.forEach { startDay ->
            val start = LocalDate.parse(WeekWindow.startOf(startDay, wednesday))
            val end = LocalDate.parse(WeekWindow.endOf(startDay, wednesday))
            assertEquals(
                "window for $startDay should span six days start to end",
                6L,
                java.time.temporal.ChronoUnit.DAYS.between(start, end),
            )
            assertEquals(
                "window for $startDay should begin on that day",
                startDay,
                start.dayOfWeek,
            )
        }
    }

    // ── isCurrent, the guard against showing last week's total ───────────

    @Test
    fun `isCurrent accepts this week and rejects last week`() {
        assertTrue(WeekWindow.isCurrent("2026-08-10", DayOfWeek.MONDAY, wednesday))
        assertFalse(WeekWindow.isCurrent("2026-08-03", DayOfWeek.MONDAY, wednesday))
    }

    @Test
    fun `isCurrent rejects a blank marker, meaning never synced`() {
        assertFalse(WeekWindow.isCurrent("", DayOfWeek.MONDAY, wednesday))
    }

    @Test
    fun `isCurrent is judged against the groups own start day`() {
        // A cache stamped by a Sunday-start group must not read as current when
        // compared using Monday, or the two would fight over the same document.
        assertFalse(WeekWindow.isCurrent("2026-08-16", DayOfWeek.MONDAY, sunday))
        assertTrue(WeekWindow.isCurrent("2026-08-16", DayOfWeek.SUNDAY, sunday))
    }

    // ── daysLeftIn, which drives notification urgency ────────────────────

    @Test
    fun `first day of the week has seven days left`() {
        assertEquals(7, WeekWindow.daysLeftIn(DayOfWeek.MONDAY, monday))
        assertEquals(7, WeekWindow.daysLeftIn(DayOfWeek.SUNDAY, sunday))
    }

    @Test
    fun `last day of the week has one day left`() {
        assertEquals(1, WeekWindow.daysLeftIn(DayOfWeek.MONDAY, sunday))
        assertEquals(1, WeekWindow.daysLeftIn(DayOfWeek.SUNDAY, saturday))
    }

    @Test
    fun `days left is never zero or negative for any start day`() {
        DayOfWeek.entries.forEach { startDay ->
            (0..6).forEach { offset ->
                val left = WeekWindow.daysLeftIn(startDay, monday.plusDays(offset.toLong()))
                assertTrue("$startDay at +$offset gave $left", left in 1..7)
            }
        }
    }

    // ── parseStartDay, the corruption guard ──────────────────────────────

    @Test
    fun `parseStartDay reads a stored name`() {
        assertEquals(DayOfWeek.SUNDAY, WeekWindow.parseStartDay("SUNDAY"))
    }

    @Test
    fun `parseStartDay falls back rather than throwing on junk`() {
        // A group document written by a later release, or corrupted, must not
        // be able to crash step aggregation for everyone in that group.
        assertEquals(DayOfWeek.MONDAY, WeekWindow.parseStartDay("FUNDAY"))
        assertEquals(DayOfWeek.MONDAY, WeekWindow.parseStartDay(null))
        assertEquals(DayOfWeek.MONDAY, WeekWindow.parseStartDay(""))
    }

    @Test
    fun `a group with no stored start day stays on Monday, not the locale`() {
        // The regression this guards: resolving legacy groups to the reader's
        // locale moved the week by a day for every group created before the
        // field existed, changing which steps counted towards the current week.
        // Groups with history keep Monday; only new groups follow the locale.
        assertEquals(DayOfWeek.MONDAY, WeekWindow.parseStartDay(null))
        assertEquals(WeekWindow.LEGACY_START_DAY, WeekWindow.parseStartDay(""))
    }

    // ── localeDefault ────────────────────────────────────────────────────

    @Test
    fun `locale default follows the region`() {
        assertEquals(DayOfWeek.SUNDAY, WeekWindow.localeDefault(Locale.US))
        assertEquals(DayOfWeek.MONDAY, WeekWindow.localeDefault(Locale.FRANCE))
    }
}
