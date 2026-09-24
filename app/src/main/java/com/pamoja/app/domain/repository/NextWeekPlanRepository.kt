package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.NextWeekPlan
import com.pamoja.app.domain.model.NextWeekPlanChoice
import com.pamoja.app.domain.model.NextWeekPlanResponse
import com.pamoja.app.domain.model.NextWeekResponse
import kotlinx.coroutines.flow.Flow

interface NextWeekPlanRepository {
    suspend fun planningWindow(groupId: String, timeZone: String): Result<com.pamoja.app.domain.model.PlanningWindow> =
        Result.failure(IllegalStateException("Planning calendar unavailable"))

    fun observeScheduledGoal(groupId: String): Flow<Result<com.pamoja.app.domain.model.ScheduledGoal?>> =
        kotlinx.coroutines.flow.flowOf(Result.success(null))

    suspend fun saveScheduledPlan(groupId: String, weekStart: String, targetSteps: Int,
        choice: NextWeekPlanChoice, sourceWeekStart: String?, basisTargetSteps: Int): Result<Unit> =
        Result.failure(IllegalStateException("Planning calendar unavailable"))

    fun observePlan(groupId: String, weekStart: String): Flow<Result<NextWeekPlan?>>

    fun observeMyResponse(
        groupId: String,
        weekStart: String,
        userId: String,
    ): Flow<Result<NextWeekPlanResponse?>>

    suspend fun savePlan(
        groupId: String,
        weekStart: String,
        targetSteps: Int,
        choice: NextWeekPlanChoice,
        source: GroupWeekSummary?,
        userId: String,
    ): Result<Unit>

    suspend fun saveResponse(
        groupId: String,
        weekStart: String,
        userId: String,
        response: NextWeekResponse,
    ): Result<Unit>
}
