package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.repository.StepRepository
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import javax.inject.Inject

class SyncTodayStepsUseCase @Inject constructor(
    private val stepRepository: StepRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.failure(AppError.SessionExpired())
        return stepRepository.syncTodaySteps(userId)
    }
}

/**
 * The caller's own steps across one group's week.
 *
 * Replaces a query that read every member's step documents. That one could not
 * be secured: a Firestore list rule is checked against what the query
 * constrains, and it named no group, so any rule permitting it also permitted
 * any signed-in account to read every user's step history. Each member now
 * publishes their own total onto their membership instead, and the leaderboard
 * reads those.
 *
 * [startDay] comes from the group, not the device. Two members of one group must
 * aggregate over the same seven days or the shared total means nothing, and a
 * user in two groups with different week starts has a different total in each.
 */
class GetMyStepsForWeekUseCase @Inject constructor(
    private val stepRepository: StepRepository
) {
    suspend operator fun invoke(
        userId: String,
        startDay: DayOfWeek,
    ): Flow<List<StepEntry>> =
        stepRepository.getStepsForUserInRange(
            userId,
            WeekWindow.startOf(startDay),
            WeekWindow.endOf(startDay),
        )
}

class GetStepsForUserUseCase @Inject constructor(
    private val stepRepository: StepRepository
) {
    suspend operator fun invoke(userId: String, date: String): Result<StepEntry> {
        if (userId.isBlank()) return Result.failure(AppError.SessionExpired())
        if (date.isBlank()) return Result.failure(AppError.Unknown("Blank date passed to step use case"))
        return stepRepository.getStepsForUser(userId, date)
    }
}