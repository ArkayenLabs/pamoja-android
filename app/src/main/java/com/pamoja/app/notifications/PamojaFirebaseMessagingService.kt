package com.pamoja.app.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.util.NotificationCategory
import com.pamoja.app.util.SmartNotificationHelper
import com.pamoja.app.util.WorkManagerScheduler
import com.pamoja.app.util.isQuietHours
import com.pamoja.app.util.minuteOfDayToTime
import com.pamoja.app.widgets.PamojaWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import javax.inject.Inject

/**
 * Receives Pamoja pushes while the app is running, and data-only ones always.
 *
 * Since 2026-10 the server sends most pushes as notification messages (an
 * android.notification block with channel, tag and loc keys) to app versions
 * that registered for them. While the app is in the background FCM draws
 * those itself and this is never called; MainActivity records them in the
 * Updates list when they are tapped or still in the tray. In the foreground,
 * and for every data-only push, they arrive here.
 */
@AndroidEntryPoint
class PamojaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var registrationManager: FcmRegistrationManager
    @Inject lateinit var notificationHelper: SmartNotificationHelper
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var workManagerScheduler: WorkManagerScheduler
    @Inject lateinit var widgetUpdater: PamojaWidgetUpdater

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
        // A cheer or anything else about a group: the widgets show it too,
        // whether or not this notification category is switched on.
        if (remote.notification.category == NotificationCategory.GROUP_ACTIVITY) widgetUpdater.refresh()
        if (!notificationHelper.isCategoryEnabled(remote.notification.category)) return

        runBlocking(Dispatchers.IO) {
            if (!userPreferences.consumeRemoteNotificationEvent(currentUid, remote.eventId)) {
                return@runBlocking
            }
            if (isQuietGroupActivity(remote.notification.category)) {
                return@runBlocking
            }

            notificationHelper.show(notificationHelper.localized(remote), remote.eventId)
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

        return isQuietHours(
            now = LocalTime.now(),
            start = minuteOfDayToTime(userPreferences.quietHoursStartMinute.first()),
            end = minuteOfDayToTime(userPreferences.quietHoursEndMinute.first()),
        )
    }

    private companion object {
        const val TAG = "PamojaMessaging"
    }
}
