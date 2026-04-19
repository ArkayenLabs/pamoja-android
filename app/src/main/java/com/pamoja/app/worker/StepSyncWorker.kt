package com.pamoja.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pamoja.app.data.local.health.HealthConnectManager
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.StepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@HiltWorker
class StepSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthConnectManager: HealthConnectManager,
    private val stepRepository: StepRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val user = authRepository.getCurrentUser()
                ?: return Result.failure()

            if (!healthConnectManager.isHealthConnectAvailable()) {
                return Result.failure()
            }

            if (!healthConnectManager.hasAllPermissions()) {
                return Result.failure()
            }

            val stepsResult = healthConnectManager.getTodaySteps()
            val steps = stepsResult.getOrElse {
                return Result.retry()
            }

            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            val stepEntry = StepEntry(
                userId = user.userId,
                stepCount = steps,
                date = today
            )

            stepRepository.saveStepEntry(stepEntry).getOrElse {
                return Result.retry()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "StepSyncWorker"
    }
}