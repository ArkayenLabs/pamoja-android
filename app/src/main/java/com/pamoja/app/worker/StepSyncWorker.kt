package com.pamoja.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.StepRepository
import com.pamoja.app.domain.analytics.AnalyticsManager
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
    private val healthConnectReader: HealthConnectReader,
    private val analyticsManager: AnalyticsManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "doWork() started | attempt=${runAttemptCount}")

        // ── Guard: user must be authenticated ───────────────────────
        val user = authRepository.getCurrentUser()
        if (user == null) {
            Log.w(TAG, "No authenticated user — failing permanently")
            return Result.failure()
        }

        analyticsManager.logStepsSyncStarted(user.userId)

        return try {
            // ── Read today's steps from Health Connect ───────────────────
            // Returns null if HC unavailable or permission revoked — silent success, no retry
            val todaySteps = healthConnectReader.readTodaySteps()
            if (todaySteps == null) {
                val durationMs = System.currentTimeMillis() - startTime
                Log.w(TAG, "Health Connect returned null (unavailable/revoked) | duration=${durationMs}ms")
                analyticsManager.logStepsSyncSkipped(user.userId, durationMs)
                return Result.success()
            }

            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            Log.d(TAG, "Read $todaySteps steps for $todayStr")

            // ── Write to Firestore (one write per worker execution) ──────
            val entry = StepEntry(
                userId    = user.userId,
                stepCount = todaySteps,
                date      = todayStr
            )
            stepRepository.saveStepEntry(entry).getOrElse { throw it }

            val durationMs = System.currentTimeMillis() - startTime
            Log.i(TAG, "Sync success | steps=$todaySteps | duration=${durationMs}ms")
            analyticsManager.logStepsSyncSuccess(user.userId, todaySteps, durationMs)

            Result.success()
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            Log.e(TAG, "Sync failed | duration=${durationMs}ms | attempt=$runAttemptCount", e)
            analyticsManager.logStepsSyncFailed(user.userId, e.message ?: "unknown", durationMs)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "StepSyncWorker"
        const val WORK_NAME = "StepSyncWorker"
    }
}