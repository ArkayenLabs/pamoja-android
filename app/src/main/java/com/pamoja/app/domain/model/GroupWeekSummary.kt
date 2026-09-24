package com.pamoja.app.domain.model

/** One member's contribution to a completed group week. */
data class GroupWeekContribution(
    val userId: String,
    val stepCount: Long,
)

/**
 * An immutable, server-finalized record of one completed group week.
 *
 * Names and photos deliberately do not live here. The future review screen can
 * join a current member's display data when available, while historical step
 * totals remain useful without preserving profile information forever. When a
 * membership ends, the backend removes that member's identifier and individual
 * contribution while keeping the completed group's aggregate result.
 */
data class GroupWeekSummary(
    val groupId: String,
    val weekStart: String,
    val weekEnd: String,
    val targetSteps: Long,
    val totalSteps: Long,
    val memberCount: Int,
    val activeMemberCount: Int,
    val contributions: List<GroupWeekContribution>,
    val finalizedAtEpochMillis: Long,
) {
    val goalHit: Boolean
        get() = targetSteps > 0L && totalSteps >= targetSteps

    val completionFraction: Float
        get() = if (targetSteps <= 0L) {
            0f
        } else {
            (totalSteps.toDouble() / targetSteps.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        }
}
