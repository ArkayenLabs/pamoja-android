package com.pamoja.app.util

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pamoja.app.worker.StepSyncWorker
import kotlinx.coroutines.flow.Flow
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

    /**
     * Runs one sync immediately, for the Sync now button in Settings.
     *
     * Reuses [StepSyncWorker] rather than reading Health Connect directly, so
     * the manual path cannot drift from the scheduled one: same auth guard,
     * same aggregation, same write, same notification check.
     *
     * REPLACE, not KEEP, because a user pressing Sync now is asking for a run
     * that starts now, not for an already-queued retry to be honoured.
     * Deliberately unconstrained: they are looking at the screen, so a failure
     * they can see beats silently waiting for the network to come back.
     */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<StepSyncWorker>().build()
        workManager.enqueueUniqueWork(
            MANUAL_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.d(TAG, "Manual step sync enqueued")
    }

    /** Emits the state of the manual sync, for the button's progress. */
    fun manualSyncState(): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(MANUAL_SYNC_WORK_NAME)

    companion object {
        private const val TAG = "WorkManagerScheduler"

        /** Separate from the periodic name so one never cancels the other. */
        const val MANUAL_SYNC_WORK_NAME = "StepSyncWorkerManual"
    }
}