package com.pamoja.app.domain.model

/** The explicit choice an organiser made for the next group week. */
enum class NextWeekPlanChoice(val wireName: String) {
    Repeat("repeat"),
    Gentler("gentler"),
    Custom("custom");

    companion object {
        fun fromWireName(value: String): NextWeekPlanChoice? =
            entries.firstOrNull { it.wireName == value }
    }
}

/** A deliberately small availability poll; it is not health or attendance data. */
enum class NextWeekResponse(val wireName: String) {
    In("in"),
    PreferGentler("prefer_gentler"),
    Resting("resting");

    companion object {
        fun fromWireName(value: String): NextWeekResponse? =
            entries.firstOrNull { it.wireName == value }
    }
}

enum class NextWeekPlanStatus(val wireName: String) {
    Scheduled("scheduled"),
    Applied("applied"),
    Missed("missed");

    companion object {
        fun fromWireName(value: String): NextWeekPlanStatus? =
            entries.firstOrNull { it.wireName == value }
    }
}

/** Group-scoped plan. It contains only aggregate history, never daily member activity. */
data class NextWeekPlan(
    val groupId: String,
    val weekStart: String,
    val targetSteps: Int,
    val choice: NextWeekPlanChoice,
    val sourceWeekStart: String?,
    val basisTotalSteps: Long?,
    val basisTargetSteps: Long,
    val status: NextWeekPlanStatus,
    val createdBy: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val appliedAtMillis: Long? = null,
    val responseCounts: NextWeekResponseCounts = NextWeekResponseCounts(),
)

data class NextWeekPlanResponse(
    val userId: String,
    val response: NextWeekResponse,
    val updatedAtMillis: Long,
)

data class NextWeekResponseCounts(
    val inCount: Int = 0,
    val preferGentlerCount: Int = 0,
    val restingCount: Int = 0,
) {
    val total: Int get() = inCount + preferGentlerCount + restingCount
}
