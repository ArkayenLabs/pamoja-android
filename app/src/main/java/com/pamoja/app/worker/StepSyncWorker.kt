package com.pamoja.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pamoja.app.data.local.health.StepCounterService
import com.pamoja.app.domain.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class StepSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val user = authRepository.getCurrentUser()
                ?: return Result.failure()
            StepCounterService.start(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "StepSyncWorker"
    }
}