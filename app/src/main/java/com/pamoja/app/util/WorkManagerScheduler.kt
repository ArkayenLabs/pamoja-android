package com.pamoja.app.util

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pamoja.app.worker.StepSyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerScheduler @Inject constructor(
    private val workManager: WorkManager
) {

    fun scheduleStepSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)   // Pause sync when battery < ~15%
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<StepSyncWorker>(
            repeatInterval         = 30,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,                     // initial delay
                TimeUnit.SECONDS        // 30s → 60s → 120s → ...
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            StepSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,   // Don't reset timer if already scheduled
            syncRequest
        )

        Log.d(TAG, "Step sync scheduled (KEEP policy, 30min interval)")
    }

    fun cancelStepSync() {
        workManager.cancelUniqueWork(StepSyncWorker.WORK_NAME)
        Log.d(TAG, "Step sync cancelled")
    }

    companion object {
        private const val TAG = "WorkManagerScheduler"
    }
}