package com.pamoja.app.domain.model

/** Only ever something to celebrate. There is no award for coming last. */
enum class WeeklyAward { MOST_STEPS, EVERY_DAY, COMEBACK }

data class AwardCandidate(
    val userId: String,
    val weeklySteps: Long,
    /** This week's [WalkedDays] mask, or null when unknown (not synced, or an older app). */
    val walkedDays: Int?,
)

/**
 * Who the weekly card celebrates, and for what.
 *
 * Deliberately small and kind: at most three people, one award each, and
 * every award is something good. Nobody is named for the fewest steps or
 * days, because the card is made to be posted where friends see it, and in a
 * mixed group the person at the bottom is usually the one who was ill,
 * injured or simply older. An award also never involves a stake of any kind.
 *
 * Worked out from this week's published figures on the phone, never stored,
 * so changing these rules later changes nothing already shared.
 */
object WeeklyAwards {
    const val MAX_FACES = 3

    /**
     * [elapsed] is how many days of the group's week have begun, today
     * included (1 on its first day, 7 on its last). [weekOver] is for a
     * finished week, where the last day counts like any other.
     */
    fun of(members: List<AwardCandidate>, elapsed: Int, weekOver: Boolean = false): List<Pair<String, WeeklyAward>> {
        val days = if (weekOver) 7 else elapsed.coerceIn(1, 7)
        val finished = if (weekOver) 7 else days - 1
        val awards = mutableListOf<Pair<String, WeeklyAward>>()
        val awarded = mutableSetOf<String>()
        fun award(userId: String, award: WeeklyAward) {
            if (awards.size < MAX_FACES && awarded.add(userId)) awards += userId to award
        }

        val bySteps = members.sortedByDescending { it.weeklySteps }
        val top = bySteps.getOrNull(0)
        val second = bySteps.getOrNull(1)
        // A tie has no single winner, and picking one of two would be arbitrary.
        if (top != null && top.weeklySteps > 0 && (second == null || top.weeklySteps > second.weeklySteps)) {
            award(top.userId, WeeklyAward.MOST_STEPS)
        }
        bySteps.filter { walkedEveryDay(it.walkedDays, finished) }.forEach { award(it.userId, WeeklyAward.EVERY_DAY) }
        bySteps.filter { cameBack(it.walkedDays, days) }.forEach { award(it.userId, WeeklyAward.COMEBACK) }
        return awards
    }

    /** Walks across the group this week: every member-day with a walk. */
    fun walks(members: List<AwardCandidate>, elapsed: Int): Int {
        val sofar = (1 shl elapsed.coerceIn(1, 7)) - 1
        return members.sumOf { WalkedDays.count((it.walkedDays ?: 0) and sofar) }
    }

    /**
     * Every finished day so far had a walk, and at least two have finished.
     * Today does not count against anyone: it is not over.
     */
    private fun walkedEveryDay(mask: Int?, finished: Int): Boolean {
        mask ?: return false
        return finished >= 2 && (0 until finished).all { WalkedDays.walked(mask, it) }
    }

    /** Missed a day earlier in the week, then walked on each of the two most recent days. */
    private fun cameBack(mask: Int?, elapsed: Int): Boolean {
        mask ?: return false
        val today = elapsed - 1
        val last = if (WalkedDays.walked(mask, today)) today else today - 1
        return last >= 2 &&
            WalkedDays.walked(mask, last) &&
            WalkedDays.walked(mask, last - 1) &&
            (0 until last - 1).any { !WalkedDays.walked(mask, it) }
    }
}
