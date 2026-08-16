package com.pamoja.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The one definition of "this week".
 *
 * A group has a single shared window, because a shared goal needs a shared
 * week: if two members computed different boundaries, the group total shown to
 * one would differ from the total shown to the other and the leaderboard would
 * disagree with itself.
 *
 * That is also why the start day is a property of the **group** rather than of
 * the user. Fitbit and its peers make it a personal preference, which is right
 * for a solo tracker; here it would let two members of one group see different
 * numbers for the same group. The admin picks it once, at creation, and it
 * applies to everyone in that group.
 *
 * `CHECKLIST.md` §2.4 names week-boundary maths as one of exactly three places
 * where a silent bug corrupts real user data, so it lives here once rather than
 * being recomputed at each call site.
 */
object WeekWindow {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * What a group created on this device should default to.
     *
     * Monday across most of Europe and Asia, Sunday in the US, Canada and
     * Japan. Read from the locale rather than hardcoded, because the previous
     * hardcoded Monday was simply wrong for a large share of the world and
     * looked like a bug to everyone in it.
     */
    fun localeDefault(locale: Locale = Locale.getDefault()): DayOfWeek =
        WeekFields.of(locale).firstDayOfWeek

    /** First day of the week containing [today], as an ISO date string. */
    fun startOf(
        startDay: DayOfWeek,
        today: LocalDate = LocalDate.now(),
    ): String =
        today.with(TemporalAdjusters.previousOrSame(startDay)).format(formatter)

    /** Last day of the week containing [today], as an ISO date string. */
    fun endOf(
        startDay: DayOfWeek,
        today: LocalDate = LocalDate.now(),
    ): String =
        today.with(TemporalAdjusters.previousOrSame(startDay))
            .plusDays(6)
            .format(formatter)

    /**
     * Whether a stored week marker still refers to the current week.
     *
     * This is what stops a cached total from a previous week being read as this
     * week's progress. A group whose members have not synced since the week
     * rolled over would otherwise show last week's figures under a fresh
     * week's goal, which is worse than showing nothing.
     */
    fun isCurrent(
        weekStart: String,
        startDay: DayOfWeek,
        today: LocalDate = LocalDate.now(),
    ): Boolean =
        weekStart.isNotBlank() && weekStart == startOf(startDay, today)

    /**
     * Days remaining in the week, counting today.
     *
     * Seven on the first day, one on the last. Previously computed inline as
     * `8 - today.dayOfWeek.value`, which silently assumed Monday.
     */
    fun daysLeftIn(
        startDay: DayOfWeek,
        today: LocalDate = LocalDate.now(),
    ): Int {
        val elapsed = ((today.dayOfWeek.value - startDay.value) + 7) % 7
        return 7 - elapsed
    }

    /**
     * Parses a stored day name back to a [DayOfWeek].
     *
     * Falls back to the locale default rather than throwing. A group document
     * written by a later release, or corrupted, must not be able to crash the
     * step aggregation for everyone in it.
     */
    fun parseStartDay(stored: String?): DayOfWeek =
        stored?.let { name ->
            DayOfWeek.entries.firstOrNull { it.name == name }
        } ?: localeDefault()
}
