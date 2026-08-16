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

    /**
     * The background schedule. Safe to call on every resume: KEEP means an
     * already-running schedule is left alone, while one that reached a terminal
     * state is re-enqueued, so this is self-healing.
     *
     * 15 minutes is WorkManager's hard floor for periodic work, not a chosen
     * number. Anything smaller is silently clamped up to it. This is the
     * background floor rather than the felt latency: [syncSoon] is what makes
     * opening the app show current figures.
     */
    fun scheduleStepSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)   // Pause sync when battery < ~15%
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<StepSyncWorker>(
            repeatInterval         = 15,
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

        Log.d(TAG, "Step sync scheduled (KEEP policy, 15min interval)")
    }

    /**
     * An opportunistic sync, for moments where the figures are about to be
     * looked at: returning to the app, and creating or joining a group.
     *
     * KEEP, not REPLACE, because these moments cluster. Opening the app right
     * after creating a group should not cancel and restart the sync that the
     * creation just kicked off.
     *
     * Distinct from [syncNow] so the two can never cancel each other, and
     * unconstrained for the same reason [syncNow] is: the user is present.
     *
     * This is the fix for groups showing no progress until something else
     * happened to trigger a sync. Callers should still rate-limit against
     * [com.pamoja.app.data.local.preferences.UserPreferences.lastSyncTime];
     * KEEP only dedupes syncs that overlap, not ones a minute apart.
     */
    fun syncSoon() {
        val request = OneTimeWorkRequestBuilder<StepSyncWorker>().build()
        workManager.enqueueUniqueWork(
            OPPORTUNISTIC_SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
        Log.d(TAG, "Opportunistic step sync enqueued")
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

        /** Separate again, so resume syncs cannot cancel the Sync now button. */
        const val OPPORTUNISTIC_SYNC_WORK_NAME = "StepSyncWorkerOpportunistic"

        /**
         * Shortest gap between two opportunistic syncs.
         *
         * Without this, flicking between apps would fire a Health Connect read
         * and a Firestore write every time. Two minutes is short enough that
         * returning to check your steps shows fresh figures, long enough that
         * app switching is not a billing event.
         */
        const val OPPORTUNISTIC_SYNC_MIN_GAP_MS = 2 * 60 * 1000L
    }
}