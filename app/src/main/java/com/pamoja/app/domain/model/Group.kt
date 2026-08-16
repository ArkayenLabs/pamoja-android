package com.pamoja.app.domain.model

data class Group(
    val groupId: String = "",
    val name: String = "",
    val adminId: String = "",
    val weeklyTarget: Int = 70000,
    val maxMemberCap: Int = 10,
    val memberCount: Int = 0,
    val canMembersEditTarget: Boolean = false,
    val inviteLink: String = "",
    val inviteLinkActive: Boolean = true,
    val createdAt: Long = 0L,
    /**
     * Cached combined steps for [weekStart], so a list of groups can show
     * progress without querying every member of every group.
     *
     * Denormalised on purpose. Deriving this on Home meant, per group, reading
     * the membership list and then those members' step entries: two live
     * listeners per group on the screen opened most often. Maintained instead by
     * [com.pamoja.app.worker.StepSyncWorker] after a sync, which already
     * computes exactly this figure for the active group.
     *
     * Always read through [WeekWindow.isCurrent]. A value left over from a
     * previous week is stale, not zero, and showing it under a fresh goal is the
     * one way this cache can lie.
     */
    val weeklySteps: Long = 0L,
    /** ISO date of the week start [weeklySteps] belongs to. Blank means never synced. */
    val weekStart: String = "",
    /**
     * Which day this group's week begins on, as a [java.time.DayOfWeek] name.
     *
     * A group-level setting rather than a per-user one. The whole product is a
     * shared weekly total, so two members on different week boundaries would
     * see different figures for the same group and the leaderboard would
     * contradict itself.
     *
     * Blank on documents written before this existed, which
     * [WeekWindow.parseStartDay] resolves to the device locale's first day.
     * Those groups were all created under a hardcoded Monday, so a member in a
     * Sunday locale will see such a group shift by a day once. Accepted: it is
     * a display window, not stored data, and the alternative is pinning every
     * existing group to Monday forever.
     */
    val weekStartDay: String = ""
) {
    /** Resolved, with the locale fallback applied. Use this, never the raw field. */
    val startDay: java.time.DayOfWeek get() = WeekWindow.parseStartDay(weekStartDay)
}