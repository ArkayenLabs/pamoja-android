package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.repository.StepRepository
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class SyncTodayStepsUseCase @Inject constructor(
    private val stepRepository: StepRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.failure(Exception("User ID cannot be empty"))
        return stepRepository.syncTodaySteps(userId)
    }
}

class GetGroupStepsForWeekUseCase @Inject constructor(
    private val stepRepository: StepRepository
) {
    suspend operator fun invoke(memberIds: List<String>): Flow<List<StepEntry>> {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val today = LocalDate.now()
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .format(formatter)
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .format(formatter)
        return stepRepository.getGroupStepsForWeek(memberIds, startOfWeek, endOfWeek)
    }
}

class GetStepsForUserUseCase @Inject constructor(
    private val stepRepository: StepRepository
) {
    suspend operator fun invoke(userId: String, date: String): Result<StepEntry> {
        if (userId.isBlank()) return Result.failure(Exception("User ID cannot be empty"))
        if (date.isBlank()) return Result.failure(Exception("Date cannot be empty"))
        return stepRepository.getStepsForUser(userId, date)
    }
}