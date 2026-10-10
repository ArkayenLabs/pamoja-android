package com.pamoja.app.data.local.billing

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pamoja.app.R
import com.pamoja.app.domain.model.Entitlement
import com.pamoja.app.util.NotificationCategory
import com.pamoja.app.util.PamojaNotification
import com.pamoja.app.util.SmartNotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The reminder the paywall promises: two days before a free trial turns
 * into a charge, say so. Google Play does not guarantee one, and a trial
 * that bills people who forgot it is how an app earns refunds and one-star
 * reviews.
 *
 * Scheduled from the entitlement, so it follows the store's own end date.
 * Never cancelled merely because the entitlement reads as free, since that
 * is also what it reads before the billing session has loaded; cancelled when
 * the subscriber is known to have cancelled or the trial has become paid.
 */
@Singleton
class TrialReminder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun sync(entitlement: Entitlement, now: Long = System.currentTimeMillis()) {
        val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (entitlement.isPremium && (!entitlement.isInTrial || !entitlement.willRenew)) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        if (!entitlement.isInTrial || entitlement.expiresAt <= 0L) return
        val delay = remindAt(entitlement.expiresAt) - now
        if (delay <= 0L) return
        work.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TrialReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_EXPIRES_AT to entitlement.expiresAt))
                .build(),
        )
    }

    companion object {
        const val WORK_NAME = "trial-reminder"
        const val KEY_EXPIRES_AT = "expiresAt"
        private val REMIND_BEFORE_MS = TimeUnit.DAYS.toMillis(2)

        fun remindAt(expiresAt: Long): Long = expiresAt - REMIND_BEFORE_MS
    }
}

@HiltWorker
class TrialReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifications: SmartNotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val expiresAt = inputData.getLong(TrialReminder.KEY_EXPIRES_AT, 0L)
        if (expiresAt <= System.currentTimeMillis()) return Result.success()
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiresAt))
        val words = ContextCompat.getContextForLanguage(applicationContext)
        notifications.show(
            PamojaNotification(
                category = NotificationCategory.REMINDER,
                title = words.getString(R.string.trial_reminder_title),
                body = words.getString(R.string.trial_reminder_body, date),
                priority = 60,
                groupId = null,
                notificationId = NOTIFICATION_ID,
            ),
        )
        return Result.success()
    }

    private companion object {
        const val NOTIFICATION_ID = 0x7121A1
    }
}
