package com.pamoja.app.domain.model

import java.time.DayOfWeek
import kotlin.math.ceil

/**
 * How a group's week is going, as the one line under its weekly total.
 *
 * Never a verdict. Falling behind reads as what it takes from here, not as
 * failing, which is the product's rule for this copy.
 */
sealed interface WeeklyPace {
    /** The goal is met. */
    data object Reached : WeeklyPace

    /** At the rate so far, the goal is met on [finishDay], inside this week. */
    data class OnPace(val finishDay: DayOfWeek, val isToday: Boolean) : WeeklyPace

    /** Too early to project, or behind: [steps] a day from here reaches it. */
    data class PerDay(val steps: Long) : WeeklyPace

    companion object {
        /**
         * [elapsedDays] is how much of the week has gone, counting the part of
         * today already past (Tuesday at 6 pm on a Monday week is 1.75), and
         * [daysLeft] counts today, as [WeekWindow.daysLeftIn] does.
         *
         * Less than a day in, a projection would be a guess from one morning,
         * so the first day always gets the per-day figure instead.
         */
        fun of(
            steps: Long,
            target: Long,
            startDay: DayOfWeek,
            elapsedDays: Double,
            daysLeft: Int,
        ): WeeklyPace {
            if (target <= 0L || steps >= target) return Reached
            val remaining = target - steps
            val perDay = PerDay((remaining + daysLeft.coerceAtLeast(1) - 1) / daysLeft.coerceAtLeast(1))
            if (elapsedDays < 1.0 || steps <= 0L) return perDay

            // Days from the week's start until the running rate reaches the
            // target, as a zero-based day index.
            val finishIndex = ceil(target / (steps / elapsedDays)).toInt() - 1
            val todayIndex = (DAYS_IN_WEEK - daysLeft).coerceIn(0, DAYS_IN_WEEK - 1)
            return if (finishIndex < DAYS_IN_WEEK) {
                val index = finishIndex.coerceAtLeast(todayIndex)
                OnPace(startDay.plus(index.toLong()), isToday = index == todayIndex)
            } else {
                perDay
            }
        }

        private const val DAYS_IN_WEEK = 7
    }
}

/**
 * A person's usual day, worked out from their own recent history rather than
 * asked for (REBUILD_PLAN.md §6).
 */
object UsualSteps {
    /** With fewer walked days than this, "usual" would be a guess. */
    const val MIN_DAYS = 3

    /**
     * The median of the days that have any steps, rounded to the nearest
     * hundred so it reads as a habit rather than a measurement. Days with no
     * steps are left out: a phone left on a desk is not a usual day. Null when
     * there is too little to go on.
     */
    fun of(recentDays: List<Long>): Long? {
        val walked = recentDays.filter { it > 0L }.sorted()
        if (walked.size < MIN_DAYS) return null
        val mid = walked.size / 2
        val median = if (walked.size % 2 == 1) walked[mid] else (walked[mid - 1] + walked[mid]) / 2
        return (median + 50L) / 100L * 100L
    }
}
