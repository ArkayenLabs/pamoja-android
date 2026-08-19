package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.StepEntry
import kotlinx.coroutines.flow.Flow

interface StepRepository {
    suspend fun saveStepEntry(stepEntry: StepEntry): Result<Unit>
    suspend fun getStepsForUser(userId: String, date: String): Result<StepEntry>
    suspend fun getStepsForUserInRange(
        userId: String,
        startDate: String,
        endDate: String
    ): Flow<List<StepEntry>>
    suspend fun syncTodaySteps(userId: String): Result<Unit>
}