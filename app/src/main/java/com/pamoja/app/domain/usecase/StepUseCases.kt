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

class GetGroupStepsForWeekUseCase @Inject constructor(
    private val stepRepository: StepRepository
) {
    /**
     * [startDay] comes from the group, not from this device. Two members of one
     * group must aggregate over the same seven days or the shared total means
     * nothing.
     */
    suspend operator fun invoke(
        memberIds: List<String>,
        startDay: DayOfWeek,
    ): Flow<List<StepEntry>> =
        stepRepository.getGroupStepsForWeek(
            memberIds,
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