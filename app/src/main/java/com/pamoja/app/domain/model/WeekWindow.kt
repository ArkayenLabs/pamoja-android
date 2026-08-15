package com.pamoja.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * The one definition of "this week".
 *
 * A group has a single shared Monday-to-Sunday window, because a shared goal
 * needs a shared week: if two members computed different boundaries, the group
 * total shown to one would differ from the total shown to the other and the
 * leaderboard would disagree with itself.
 *
 * `CHECKLIST.md` §2.4 names week-boundary maths as one of exactly three places
 * where a silent bug corrupts real user data, so it lives here once rather than
 * being recomputed at each call site. There is no test suite covering it yet;
 * that is the argument for having one copy, not three.
 */
object WeekWindow {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Monday of the week containing [today], as an ISO date string. */
    fun startOf(today: LocalDate = LocalDate.now()): String =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(formatter)

    /** Sunday of the week containing [today], as an ISO date string. */
    fun endOf(today: LocalDate = LocalDate.now()): String =
        today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).format(formatter)

    /**
     * Whether a stored week marker still refers to the current week.
     *
     * This is what stops a cached total from a previous week being read as this
     * week's progress. A group whose members have not synced since Sunday would
     * otherwise show last week's figures under a fresh week's goal, which is
     * worse than showing nothing.
     */
    fun isCurrent(weekStart: String, today: LocalDate = LocalDate.now()): Boolean =
        weekStart.isNotBlank() && weekStart == startOf(today)
}
