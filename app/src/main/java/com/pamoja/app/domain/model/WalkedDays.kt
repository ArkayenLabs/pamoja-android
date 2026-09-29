package com.pamoja.app.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Which days of a group's week someone walked, as the 7-bit mask stored on
 * their membership (`walkedDays`). Bit 0 is the week's first day.
 *
 * A mask rather than per-day figures on purpose: the standings only need
 * "walked or not", and other members already see the weekly and daily totals.
 * Nobody's day-by-day step history leaves the owner-only steps collection.
 */
object WalkedDays {
    /**
     * What counts as a walk: about ten minutes. A phone carried round the
     * house records a few hundred steps, and that is not a walk worth a dot.
     */
    const val MIN_STEPS = 1_000L

    /** Every day of the week. Also the largest value the rules accept. */
    const val ALL = 0b111_1111

    /** The mask for the week starting [weekStart], from that week's step entries. */
    fun of(entries: List<StepEntry>, weekStart: LocalDate): Int =
        entries.fold(0) { mask, entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@fold mask
            val index = ChronoUnit.DAYS.between(weekStart, date)
            if (index in 0..6 && entry.stepCount >= MIN_STEPS) mask or (1 shl index.toInt()) else mask
        }

    fun walked(mask: Int, dayIndex: Int): Boolean = dayIndex in 0..6 && (mask shr dayIndex) and 1 == 1

    fun count(mask: Int): Int = Integer.bitCount(mask and ALL)
}
