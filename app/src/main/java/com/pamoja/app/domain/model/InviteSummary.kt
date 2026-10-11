package com.pamoja.app.domain.model

/**
 * What an invite shows before sign-in (REBUILD_PLAN.md §6): the group's name,
 * size and goal, and the first name of whoever started it. Deliberately not a
 * [Group]: nothing here may grow into a member list, a photo or anyone's steps.
 */
data class InviteSummary(
    val groupId: String,
    val name: String,
    val memberCount: Int,
    val maxMemberCap: Int,
    val weeklyTarget: Int,
    val isFull: Boolean,
    /** Null when the group's starter has left or has no name on record. */
    val startedBy: String?,
)
