package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.repository.StepRepository
import kotlinx.coroutines.flow.Flow
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
    suspend operator fun invoke(memberIds: List<String>): Flow<List<StepEntry>> =
        stepRepository.getGroupStepsForWeek(memberIds, WeekWindow.startOf(), WeekWindow.endOf())
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