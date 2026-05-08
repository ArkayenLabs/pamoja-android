package com.pamoja.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.StepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Runs every 30 minutes (when network is available and battery is not low).
 *
 * Uses Health Connect to read steps — no foreground service, no hardware
 * sensor registration. Health Connect is a system-level store managed by
 * Google, making it reliable across OEM battery optimisers.
 *
 * Execution per run:
 *  1. Verify user is logged in (fail fast if not).
 *  2. Read today's step total from Health Connect.
 *  3. Write exactly ONE StepEntry to Firestore.
 *  4. Done.
 */
@HiltWorker
class StepSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val stepRepository: StepRepository,
    private val healthConnectReader: HealthConnectReader
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // ── Guard: user must be authenticated ───────────────────────
            val user = authRepository.getCurrentUser() ?: return Result.failure()

            // ── Read today's steps from Health Connect ───────────────────
            // Returns null if HC unavailable or permission revoked — silent success, no retry
            val todaySteps = healthConnectReader.readTodaySteps()
                ?: return Result.success()

            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            // ── Write to Firestore (one write per worker execution) ──────
            val entry = StepEntry(
                userId    = user.userId,
                stepCount = todaySteps,
                date      = todayStr
            )
            stepRepository.saveStepEntry(entry)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "StepSyncWorker"
    }
}