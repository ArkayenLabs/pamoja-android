package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.NextWeekPlan
import com.pamoja.app.domain.model.NextWeekPlanChoice
import com.pamoja.app.domain.model.NextWeekPlanResponse
import com.pamoja.app.domain.model.NextWeekResponse
import com.pamoja.app.domain.model.StepGoal
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.repository.NextWeekPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

class ObserveNextWeekPlanUseCase @Inject constructor(
    private val repository: NextWeekPlanRepository,
) {
    suspend fun planningWindow(groupId: String) = repository.planningWindow(groupId, ZoneId.systemDefault().id)
    fun scheduledGoal(groupId: String) = repository.observeScheduledGoal(groupId)

    operator fun invoke(groupId: String, weekStart: String): Flow<Result<NextWeekPlan?>> =
        if (groupId.isBlank() || !weekStart.isIsoDate()) flowOf(Result.success(null))
        else repository.observePlan(groupId, weekStart)
}

class ObserveMyNextWeekResponseUseCase @Inject constructor(
    private val repository: NextWeekPlanRepository,
) {
    operator fun invoke(
        groupId: String,
        weekStart: String,
        userId: String,
    ): Flow<Result<NextWeekPlanResponse?>> =
        if (groupId.isBlank() || userId.isBlank() || !weekStart.isIsoDate()) {
            flowOf(Result.success(null))
        } else {
            repository.observeMyResponse(groupId, weekStart, userId)
        }
}

class SaveNextWeekPlanUseCase @Inject constructor(
    private val repository: NextWeekPlanRepository,
) {
    suspend operator fun invoke(
        group: Group,
        userId: String,
        weekStart: String,
        choice: NextWeekPlanChoice,
        customTarget: Int?,
        source: GroupWeekSummary?,
        authoritative: Boolean = false,
    ): Result<Unit> {
        if (group.adminId != userId) return Result.failure(AppError.PermissionDenied())
        if (source != null && (source.groupId != group.groupId || source.weekStart.isBlank())) {
            return Result.failure(AppError.Conflict("The historical basis belongs to another group"))
        }
        val planDate = runCatching { LocalDate.parse(weekStart) }.getOrNull()
        val sourceDate = source?.let { runCatching { LocalDate.parse(it.weekStart) }.getOrNull() }
        if (planDate == null || planDate.dayOfWeek != group.startDay ||
            (source != null && (sourceDate == null || !sourceDate.isBefore(planDate)))
        ) {
            return Result.failure(AppError.Conflict("The next-week plan is out of date"))
        }
        val target = targetForChoice(group.weeklyTarget, choice, customTarget)
            ?: return Result.failure(AppError.Validation(ValidationField.WeeklyTargetInvalid))
        if (authoritative) return repository.saveScheduledPlan(group.groupId, weekStart,
            target, choice, source?.weekStart, group.weeklyTarget)
        return repository.savePlan(
            groupId = group.groupId,
            weekStart = weekStart,
            targetSteps = target,
            choice = choice,
            source = source,
            userId = userId,
        )
    }
}

class SaveNextWeekResponseUseCase @Inject constructor(
    private val repository: NextWeekPlanRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        weekStart: String,
        userId: String,
        response: NextWeekResponse,
    ): Result<Unit> {
        if (groupId.isBlank() || userId.isBlank() || !weekStart.isIsoDate()) {
            return Result.failure(AppError.SessionExpired())
        }
        return repository.saveResponse(groupId, weekStart, userId, response)
    }
}

internal fun targetForChoice(
    currentTarget: Int,
    choice: NextWeekPlanChoice,
    customTarget: Int?,
): Int? = when (choice) {
    NextWeekPlanChoice.Repeat -> currentTarget.takeIf(StepGoal::isSelectableWeeklyTotal)
    NextWeekPlanChoice.Gentler -> {
        val rounded = ((currentTarget * GENTLER_FACTOR) / TARGET_ROUNDING).roundToInt() * TARGET_ROUNDING
        rounded.coerceAtLeast(StepGoal.MIN_SELECTABLE_WEEKLY_TOTAL)
            .takeIf(StepGoal::isSelectableWeeklyTotal)
    }
    NextWeekPlanChoice.Custom -> customTarget?.takeIf(StepGoal::isSelectableWeeklyTotal)
}

fun upcomingWeekStart(group: Group, clock: Clock): String {
    val storedStart = runCatching { LocalDate.parse(group.weekStart) }.getOrNull()
    val calendarStart = LocalDate.parse(
        WeekWindow.startOf(
            group.startDay,
            LocalDate.now(clock.withZone(ZoneId.systemDefault())),
        ),
    )
    // A group may not have synced for several weeks. Never schedule a plan for
    // a stale cached boundary just because that old ISO date still parses.
    val currentStart = maxOf(storedStart ?: calendarStart, calendarStart)
    return currentStart.plusDays(7).toString()
}

private fun String.isIsoDate(): Boolean = runCatching {
    LocalDate.parse(this).toString() == this
}.getOrDefault(false)

private const val GENTLER_FACTOR = 0.8
private const val TARGET_ROUNDING = 10_000
