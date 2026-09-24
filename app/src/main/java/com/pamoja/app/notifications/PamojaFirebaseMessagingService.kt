package com.pamoja.app.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.util.NotificationCategory
import com.pamoja.app.util.SmartNotificationHelper
import com.pamoja.app.util.WorkManagerScheduler
import com.pamoja.app.util.minuteOfDayToTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import javax.inject.Inject

/** Receives Pamoja data messages in foreground, background, and cold states. */
@AndroidEntryPoint
class PamojaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var registrationManager: FcmRegistrationManager
    @Inject lateinit var notificationHelper: SmartNotificationHelper
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var workManagerScheduler: WorkManagerScheduler

    override fun onRegistered(installationId: String) {
        registrationManager.onRegistered(installationId)
    }

    override fun onUnregistered(installationId: String) {
        registrationManager.onUnregistered(installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val remote = RemoteNotificationParser.parse(message.data) ?: return
        val currentUid = auth.currentUser?.uid ?: return

        // A stale registration must never show one account's group details to
        // another account that later signs in on the same physical device.
        if (remote.recipientUid != currentUid) return
        if (!notificationHelper.isCategoryEnabled(remote.notification.category)) return

        runBlocking(Dispatchers.IO) {
            if (!userPreferences.consumeRemoteNotificationEvent(currentUid, remote.eventId)) {
                return@runBlocking
            }
            if (isQuietGroupActivity(remote.notification.category)) {
                return@runBlocking
            }

            notificationHelper.show(remote.notification)
            userPreferences.saveLastNotificationTime(System.currentTimeMillis())
            userPreferences.incrementIgnoredNotifications()
            Log.i(TAG, "Remote notification shown [${remote.notification.category}]")
        }
    }

    override fun onDeletedMessages() {
        // FCM recommends a full application sync after it drops queued data.
        // Pamoja's existing worker already owns that authoritative refresh.
        workManagerScheduler.syncSoon()
    }

    private suspend fun isQuietGroupActivity(category: NotificationCategory): Boolean {
        if (category != NotificationCategory.GROUP_ACTIVITY) return false

        val now = LocalTime.now()
        val start = minuteOfDayToTime(userPreferences.quietHoursStartMinute.first())
        val end = minuteOfDayToTime(userPreferences.quietHoursEndMinute.first())
        return if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
    }

    private companion object {
        const val TAG = "PamojaMessaging"
    }
}
