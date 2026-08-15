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
    /** ISO date of the Monday [weeklySteps] belongs to. Blank means never synced. */
    val weekStart: String = ""
)