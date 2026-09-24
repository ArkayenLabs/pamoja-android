package com.pamoja.app.data.remote.model

import com.google.firebase.Timestamp
import com.pamoja.app.domain.model.GroupWeekContribution
import com.pamoja.app.domain.model.GroupWeekSummary
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class GroupWeekContributionDto(
    val userId: String = "",
    val stepCount: Long = 0L,
)

/** Firestore shape written only by finalizeCompletedGroupWeek. */
data class GroupWeekSummaryDto(
    val schemaVersion: Int = 0,
    val groupId: String = "",
    val weekStart: String = "",
    val weekEnd: String = "",
    val targetSteps: Long = 0L,
    val totalSteps: Long = 0L,
    val memberCount: Int = 0,
    val activeMemberCount: Int = 0,
    val contributions: List<GroupWeekContributionDto> = emptyList(),
    val finalizedAt: Timestamp? = null,
) {
    /**
     * Refuses shapes the app cannot explain instead of drawing a plausible but
     * false history row. Numeric clamping is defense in depth for old emulator
     * data; production writes are already bounded by the backend.
     */
    fun toDomain(): GroupWeekSummary? {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) return null
        if (groupId.isBlank() || !weekStart.isIsoDate() || !weekEnd.isIsoDate()) return null

        val safeContributions = contributions
            .asSequence()
            .filter { it.userId.isNotBlank() && !it.userId.contains('/') }
            .distinctBy { it.userId }
            .take(MAX_GROUP_MEMBERS)
            .map {
                GroupWeekContribution(
                    userId = it.userId,
                    stepCount = it.stepCount.coerceIn(0L, MAX_WEEKLY_MEMBER_STEPS),
                )
            }
            .toList()

        return GroupWeekSummary(
            groupId = groupId,
            weekStart = weekStart,
            weekEnd = weekEnd,
            targetSteps = targetSteps.coerceIn(0L, MAX_GROUP_TARGET_STEPS),
            totalSteps = totalSteps.coerceIn(0L, MAX_GROUP_WEEKLY_STEPS),
            // These counts deliberately do not derive from the contribution
            // list. A former member's identifier is redacted after they leave,
            // while the honest historical participation count remains.
            memberCount = memberCount.coerceIn(0, MAX_GROUP_MEMBERS),
            activeMemberCount = activeMemberCount.coerceIn(
                0,
                memberCount.coerceIn(0, MAX_GROUP_MEMBERS),
            ),
            contributions = safeContributions,
            finalizedAtEpochMillis = finalizedAt?.toDate()?.time ?: 0L,
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_GROUP_MEMBERS = 20
        const val MAX_WEEKLY_MEMBER_STEPS = 2_100_000L
        const val MAX_GROUP_WEEKLY_STEPS = 42_000_000L
        const val MAX_GROUP_TARGET_STEPS = 2_800_000L
    }
}

private fun String.isIsoDate(): Boolean = try {
    LocalDate.parse(this).toString() == this
} catch (_: DateTimeParseException) {
    false
}
