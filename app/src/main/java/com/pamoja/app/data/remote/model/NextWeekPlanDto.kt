package com.pamoja.app.data.remote.model

import com.google.firebase.Timestamp
import com.pamoja.app.domain.model.NextWeekPlan
import com.pamoja.app.domain.model.NextWeekPlanChoice
import com.pamoja.app.domain.model.NextWeekPlanResponse
import com.pamoja.app.domain.model.NextWeekPlanStatus
import com.pamoja.app.domain.model.NextWeekResponse
import com.pamoja.app.domain.model.StepGoal
import java.time.LocalDate

data class NextWeekPlanDto(
    val schemaVersion: Int = 0,
    val groupId: String = "",
    val weekStart: String = "",
    val targetSteps: Int = 0,
    val choice: String = "",
    val sourceWeekStart: String? = null,
    val basisTotalSteps: Long? = null,
    val basisTargetSteps: Long = 0L,
    val status: String = "",
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val appliedAt: Timestamp? = null,
    val inCount: Int = 0,
    val preferGentlerCount: Int = 0,
    val restingCount: Int = 0,
) {
    fun toDomain(): NextWeekPlan? {
        if (schemaVersion !in setOf(SCHEMA_VERSION, DAY_ONE_SCHEMA_VERSION) ||
            groupId.isBlank() || createdBy.isBlank()
        ) return null
        if (!weekStart.isIsoDate()) return null
        if (schemaVersion == SCHEMA_VERSION || sourceWeekStart != null) {
            if (sourceWeekStart?.isIsoDate() != true || basisTotalSteps == null) return null
        } else if (basisTotalSteps != null || basisTargetSteps < 10_000L ||
            !StepGoal.isSelectableWeeklyTotal(basisTargetSteps.toInt()) ||
            basisTargetSteps > StepGoal.MAX_WEEKLY_TOTAL
        ) return null
        if (!StepGoal.isSelectableWeeklyTotal(targetSteps)) return null
        val parsedChoice = NextWeekPlanChoice.fromWireName(choice) ?: return null
        val parsedStatus = NextWeekPlanStatus.fromWireName(status) ?: return null
        return NextWeekPlan(
            groupId = groupId,
            weekStart = weekStart,
            targetSteps = targetSteps,
            choice = parsedChoice,
            sourceWeekStart = sourceWeekStart,
            basisTotalSteps = basisTotalSteps?.coerceIn(0L, MAX_GROUP_WEEKLY_STEPS),
            basisTargetSteps = basisTargetSteps.coerceIn(0L, StepGoal.MAX_WEEKLY_TOTAL.toLong()),
            status = parsedStatus,
            createdBy = createdBy,
            createdAtMillis = createdAt?.toDate()?.time ?: 0L,
            updatedAtMillis = updatedAt?.toDate()?.time ?: 0L,
            appliedAtMillis = appliedAt?.toDate()?.time,
            responseCounts = com.pamoja.app.domain.model.NextWeekResponseCounts(
                inCount = inCount.coerceIn(0, MAX_RESPONSES),
                preferGentlerCount = preferGentlerCount.coerceIn(0, MAX_RESPONSES),
                restingCount = restingCount.coerceIn(0, MAX_RESPONSES),
            ),
        )
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val DAY_ONE_SCHEMA_VERSION = 2
        private const val MAX_GROUP_WEEKLY_STEPS = 42_000_000L
        private const val MAX_RESPONSES = 20
    }
}

data class NextWeekPlanResponseDto(
    val schemaVersion: Int = 0,
    val groupId: String = "",
    val weekStart: String = "",
    val userId: String = "",
    val response: String = "",
    val updatedAt: Timestamp? = null,
) {
    fun toDomain(): NextWeekPlanResponse? {
        if (schemaVersion != NextWeekPlanDto.SCHEMA_VERSION || userId.isBlank()) return null
        val parsed = NextWeekResponse.fromWireName(response) ?: return null
        return NextWeekPlanResponse(
            userId = userId,
            response = parsed,
            updatedAtMillis = updatedAt?.toDate()?.time ?: 0L,
        )
    }
}

private fun String.isIsoDate(): Boolean = runCatching {
    LocalDate.parse(this).toString() == this
}.getOrDefault(false)
