package com.pamoja.app.domain.model

/**
 * The single source of truth for Pamoja's shared weekly group goal.
 *
 * A group chooses one stable total. Everyone's steps pool into it, but nobody
 * receives an individual quota. That matters for families and mixed-ability
 * groups: one person may contribute a short daily walk while another contributes
 * much more, and both are helping the same promise.
 *
 * Member count is deliberately independent. Joining or leaving never moves the
 * finish line, and changing the member cap never rewrites the goal.
 */
object StepGoal {

    /** Fast starting points. They are suggestions, not the only valid goals. */
    val PRESETS_WEEKLY_TOTAL = listOf(35_000, 50_000, 70_000, 100_000, 150_000)

    const val MIN_SELECTABLE_WEEKLY_TOTAL = 10_000
    const val MAX_SELECTABLE_WEEKLY_TOTAL = 2_800_000
    const val DEFAULT_WEEKLY_TOTAL = 70_000

    /** The largest group the product permits. Kept here for shared validation. */
    const val MAX_GROUP_MEMBERS = 20

    /**
     * Backward-compatible storage ceiling.
     *
     * A short-lived per-person model could create totals well above the quick
     * presets. The same ceiling is used for custom totals so those groups remain
     * editable without silently changing their existing promise.
     */
    const val MAX_WEEKLY_TOTAL = 2_800_000

    fun isPresetWeeklyTotal(weeklyTotal: Int): Boolean =
        weeklyTotal in PRESETS_WEEKLY_TOTAL

    fun isSelectableWeeklyTotal(weeklyTotal: Int): Boolean =
        weeklyTotal in MIN_SELECTABLE_WEEKLY_TOTAL..MAX_SELECTABLE_WEEKLY_TOTAL

    fun isValidWeeklyTotal(weeklyTotal: Int): Boolean =
        isSelectableWeeklyTotal(weeklyTotal)

    /**
     * Edit options preserve a non-standard older total as an explicit choice.
     * It is never rounded or replaced merely because another setting was saved.
     */
    fun editOptionsFor(currentWeeklyTotal: Int): List<Int> =
        (PRESETS_WEEKLY_TOTAL + currentWeeklyTotal)
            .filter(::isValidWeeklyTotal)
            .distinct()
            .sorted()
}
