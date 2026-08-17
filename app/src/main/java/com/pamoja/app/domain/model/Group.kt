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
     * [WeekWindow.parseStartDay] resolves to **Monday**, the day all of those
     * groups actually ran on.
     *
     * It briefly resolved to the device locale instead, which silently moved
     * the week for every existing group read from a Sunday-first locale: the
     * header still said Mon-Sun while the settings screen and the aggregation
     * had moved to Sunday. The locale is the right default for a group being
     * created, not for one that already has history.
     */
    val weekStartDay: String = "",
    /**
     * The group's photo, shown wherever the group is listed.
     *
     * Blank means no photo, and the gradient tile with the group's initials is
     * the designed state rather than a fallback. Only the admin can set it:
     * this field is in the admin-only allowlist in firestore.rules case B.
     */
    val photoUrl: String = ""
) {
    /** Resolved, with the locale fallback applied. Use this, never the raw field. */
    val startDay: java.time.DayOfWeek get() = WeekWindow.parseStartDay(weekStartDay)
}