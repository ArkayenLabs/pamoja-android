package com.pamoja.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pamoja.app.data.local.health.StepReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.StepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Runs every 30 minutes (when network is available and battery is not low).
 *
 * Execution per run:
 *  1. Verify user is logged in (fail fast if not).
 *  2. Read hardware step counter ONCE via StepReader (~100–200 ms).
 *  3. Determine today's step count using a stored daily baseline:
 *       - If baseline is from a previous day → reset baseline to current hardware value (new day).
 *       - If hardware count < baseline (device rebooted mid-day) → treat current value as the steps.
 *       - Otherwise → todaySteps = hardwareCount - baselineValue.
 *  4. Write exactly ONE StepEntry to Firestore.
 *  5. Done. Total Firestore writes per user per day: max 48 (vs. previous 8,000–12,000).
 */
@HiltWorker
class StepSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val stepRepository: StepRepository,
    private val stepReader: StepReader,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // ── Guard: user must be authenticated ───────────────────────
            val user = authRepository.getCurrentUser() ?: return Result.failure()

            // ── Read hardware step counter (one-shot, <200ms) ───────────
            val hardwareCount = stepReader.readCurrentStepCount()
                ?: return Result.success()   // Device has no sensor — silent success, no retry

            val today         = LocalDate.now()
            val todayStr      = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

            // ── Calculate today's steps using stored baseline ────────────
            val baselineDate  = userPreferences.stepBaselineDate.first()
            val baselineValue = userPreferences.stepBaselineValue.first()

            val todaySteps: Long
            when {
                // New calendar day (or first ever run) → reset baseline
                baselineDate != todayStr -> {
                    userPreferences.saveStepBaseline(todayStr, hardwareCount)
                    todaySteps = 0L   // Just started the day — report 0, will grow from here
                }
                // Device rebooted mid-day (counter reset to 0 or smaller value)
                hardwareCount < baselineValue -> {
                    userPreferences.saveStepBaseline(todayStr, hardwareCount)
                    todaySteps = hardwareCount   // Steps since reboot = entire hardware count
                }
                // Normal case — delta from today's baseline
                else -> {
                    todaySteps = hardwareCount - baselineValue
                }
            }

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