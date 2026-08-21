package com.pamoja.app.domain.model

data class Group(
    val groupId: String = "",
    val name: String = "",
    val adminId: String = "",
    /**
     * The group's weekly total, still absolute and still what everything
     * compares against.
     *
     * **Derived from [dailyPerPersonTarget], not chosen directly.** See
     * [StepGoal] for why the per-person figure is the real setting and this is
     * the computed one. Recomputed at week rollover rather than when someone
     * joins, so the bar never moves under people mid-week.
     */
    val weeklyTarget: Int = StepGoal.weeklyTotalFor(
        StepGoal.DEFAULT_DAILY_PER_PERSON,
        members = 1,
    ),
    /**
     * The actual setting: steps per person per day.
     *
     * **Zero means a legacy group**, created before the goal was expressed this
     * way. Those keep whatever [weeklyTarget] they were given rather than
     * having it silently rewritten, so read [effectiveDailyPerPerson] rather
     * than this field when showing a number to anyone.
     */
    val dailyPerPersonTarget: Int = StepGoal.DEFAULT_DAILY_PER_PERSON,
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

    /** True for a group created before the goal was expressed per person. */
    val hasLegacyGoal: Boolean get() = dailyPerPersonTarget <= 0

    /**
     * The per-person figure to show, for any group.
     *
     * For a legacy group this is back-computed from the total it was given,
     * which is the only way to put a human number next to a goal nobody chose
     * per person. Show this, never [dailyPerPersonTarget].
     */
    val effectiveDailyPerPerson: Int
        get() = if (hasLegacyGoal) {
            StepGoal.dailyPerPersonFor(weeklyTarget, memberCount)
        } else {
            dailyPerPersonTarget
        }

    /**
     * What [weeklyTarget] would be if it were recomputed for the current
     * membership right now.
     *
     * Not applied automatically: recomputing on join would move the bar
     * mid-week and drop everyone's progress the moment a friend joined, which
     * punishes the exact behaviour the product wants. The rollover job applies
     * it, and the edit screen shows it as a preview.
     */
    val weeklyTargetForCurrentMembers: Int
        get() = StepGoal.weeklyTotalFor(effectiveDailyPerPerson, memberCount)
}