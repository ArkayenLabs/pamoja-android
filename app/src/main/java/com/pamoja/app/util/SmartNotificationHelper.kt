package com.pamoja.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pamoja.app.MainActivity
import com.pamoja.app.R
import com.pamoja.app.data.local.activity.ActivityLogStore
import com.pamoja.app.domain.model.NotificationCategory as DomainNotificationCategory
import com.pamoja.app.notifications.RemoteNotification
import com.pamoja.app.notifications.RemoteNotificationParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts notifications: the engine's, the trial reminder, and pushes that
 * arrive while the app is running. Pushes that arrive while it is not are
 * drawn by FCM itself, on the same channels.
 *
 * Four separate channels, deliberately. Android lets users disable channels
 * individually, with a single channel, someone annoyed by daily reminders has to
 * mute *everything*, including the group-hit-its-goal moment that is the
 * emotional payoff of the product. Splitting them lets the reminder channel
 * absorb the annoyance while the high-value channels survive.
 */
@Singleton
open class SmartNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityLog: ActivityLogStore,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * For the log write, which is suspending while [show] is not.
     *
     * Its own scope rather than a caller's: this is fire and forget, and it
     * must not be cancelled just because whatever triggered the notification
     * finished first. `show` is called from workers whose scope ends the
     * moment they return.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        createChannels()
    }

    /**
     * Writes the channel names again, in the language chosen now. Android
     * keeps a channel's name until the app sets it, so after a language
     * change the system's notification settings would otherwise keep the old
     * one until the next process start. Importance is never touched.
     */
    fun refreshChannelNames() = createChannels()

    /**
     * Removes everything this app has in the tray, on sign-out and account
     * deletion. Pushes name other people ("High five from Ravi") and their
     * groups, and they would otherwise stay on a phone that has been handed
     * to someone else.
     */
    fun clearShown() = notificationManager.cancelAll()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // The app's language, not the phone's, below Android 13 too.
        val words = ContextCompat.getContextForLanguage(context)

        // Names and descriptions are the Settings rows' own strings, so the
        // two screens agree, in the reader's language. Android renames an
        // existing channel when these change; it never changes its importance.
        val channels = listOf(
            // Rarely muted, genuinely good news. Worth a vibration.
            channel(
                words = words,
                id = NotificationCategory.ACHIEVEMENT.channelId,
                name = R.string.notif_achievement_title,
                description = R.string.notif_achievement_sub,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                vibrate = true,
            ),
            // Social news from real people, relevant, so default importance.
            channel(
                words = words,
                id = NotificationCategory.GROUP_ACTIVITY.channelId,
                name = R.string.notif_group_title,
                description = R.string.notif_group_sub,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                vibrate = false,
            ),
            // The category people mute. Low importance: no sound, no peeking.
            channel(
                words = words,
                id = NotificationCategory.REMINDER.channelId,
                name = R.string.notif_reminder_title,
                description = R.string.notif_reminder_sub,
                importance = NotificationManager.IMPORTANCE_LOW,
                vibrate = false,
            ),
            channel(
                words = words,
                id = NotificationCategory.RECAP.channelId,
                name = R.string.notif_recap_title,
                description = R.string.notif_recap_sub,
                importance = NotificationManager.IMPORTANCE_LOW,
                vibrate = false,
            ),
        )

        notificationManager.createNotificationChannels(channels)

        // Remove the old single channel. Its user-visible name was
        // "Step reminders & roasts", which no longer reflects how this app
        // speaks to people.
        runCatching { notificationManager.deleteNotificationChannel("pamoja_roasts_channel") }
    }

    private fun channel(
        words: Context,
        id: String,
        @StringRes name: Int,
        @StringRes description: Int,
        importance: Int,
        vibrate: Boolean,
    ): NotificationChannel =
        NotificationChannel(id, words.getString(name), importance).apply {
            this.description = words.getString(description)
            enableVibration(vibrate)
            // Health-adjacent content should not be readable from a locked screen.
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }

    /**
     * Whether the system will actually deliver this category.
     *
     * The Android channel is the single source of truth for this, and the app
     * used to keep a second one in DataStore. Two switches for one thing, and
     * the OS exposes its own in system settings, so they could disagree: a
     * category muted in Settings but live in Android, or the reverse, with
     * neither screen admitting the other existed.
     *
     * A null channel means it has not been created yet, which is not the same
     * as blocked, so it reads as enabled.
     */
    fun isCategoryEnabled(category: NotificationCategory): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = notificationManager.getNotificationChannel(category.channelId)
            ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** The categories the system is currently refusing, for the engine. */
    open fun mutedCategories(): Set<NotificationCategory> =
        NotificationCategory.entries.filterNot { isCategoryEnabled(it) }.toSet()

    /**
     * Where to send someone who wants to change a category.
     *
     * Android does not let an app raise or lower a channel's importance once
     * the channel exists; that is the user's to set and cannot be written
     * programmatically. So the in-app control reflects the real state and hands
     * over to the system screen for the actual change, which is the only way
     * the two can be guaranteed never to diverge.
     */
    fun channelSettingsIntent(category: NotificationCategory): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, category.channelId)
        }

    /**
     * Posts a notification. No-ops if the user has revoked the permission.
     * [eventId] is a push's own id, so the Updates list holds it once however
     * it is seen (see [recordTrayNotifications]).
     */
    open fun show(notification: PamojaNotification, eventId: String? = null) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val builder = NotificationCompat.Builder(context, notification.category.channelId)
            // Must be a white silhouette. Android uses only the alpha channel.
            // Passing the launcher icon here is why it used to render as a blob.
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.pamoja_brand))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            // Bodies get truncated in the collapsed tray; this makes the full
            // text readable when expanded.
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setPriority(toCompatPriority(notification.category))
            .setContentIntent(contentIntent(notification))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        val posted = runCatching {
            notificationManager.notify(notification.notificationId, builder.build())
        }.isSuccess

        // Recorded only if it actually went out, and recorded here rather than
        // at the call site so the activity log cannot drift from what the user
        // was really shown. The early return above already covers the case
        // where notifications are switched off at the OS level.
        if (posted) {
            scope.launch {
                activityLog.record(
                    category = notification.category.toDomain(),
                    title = notification.title,
                    body = notification.body,
                    groupId = notification.groupId,
                    eventId = eventId,
                )
            }
        }
    }

    /**
     * Adds to the Updates list the pushes FCM drew while the app was not
     * running, which never passed through [show]. Read off the tray, so a
     * push swiped away before the app was next opened is missed; one that was
     * tapped is recorded by [recordOpened] instead.
     */
    fun recordTrayNotifications() {
        val delivered = runCatching { notificationManager.activeNotifications }.getOrNull() ?: return
        delivered.forEach { posted ->
            val (groupId, eventId) = RemoteNotificationParser.parseTrayTag(posted.tag) ?: return@forEach
            val category = RemoteNotificationParser.categoryOfChannel(posted.notification.channelId)
                ?: return@forEach
            val extras = posted.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return@forEach
            val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            scope.launch {
                activityLog.record(category.toDomain(), title, body, groupId, eventId)
            }
        }
    }

    /** Records a tapped push, in this phone's language when it has the words. */
    fun recordOpened(remote: RemoteNotification) {
        val shown = localized(remote)
        scope.launch {
            activityLog.record(
                category = shown.category.toDomain(),
                title = shown.title,
                body = shown.body,
                groupId = shown.groupId,
                eventId = remote.eventId,
            )
        }
    }

    /** The push's words from this build's strings, falling back to the server's English. */
    fun localized(remote: RemoteNotification): PamojaNotification =
        remote.notification.copy(
            title = NotificationCopy.pushText(context, remote.title) ?: remote.notification.title,
            body = NotificationCopy.pushText(context, remote.body) ?: remote.notification.body,
        )

    /**
     * The engine's category as the domain's.
     *
     * Two enums with the same members, kept apart deliberately: the engine's
     * carries an Android channel id, and the activity log has no business
     * knowing about channels.
     */
    private fun NotificationCategory.toDomain(): DomainNotificationCategory = when (this) {
        NotificationCategory.ACHIEVEMENT -> DomainNotificationCategory.ACHIEVEMENT
        NotificationCategory.GROUP_ACTIVITY -> DomainNotificationCategory.GROUP_ACTIVITY
        NotificationCategory.REMINDER -> DomainNotificationCategory.REMINDER
        NotificationCategory.RECAP -> DomainNotificationCategory.RECAP
    }

    private fun toCompatPriority(category: NotificationCategory): Int = when (category) {
        NotificationCategory.ACHIEVEMENT,
        NotificationCategory.GROUP_ACTIVITY -> NotificationCompat.PRIORITY_DEFAULT

        NotificationCategory.REMINDER,
        NotificationCategory.RECAP -> NotificationCompat.PRIORITY_LOW
    }

    /**
     * Opens the group the notification is about, rather than dumping the user on
     * the start destination. [MainActivity] reads [EXTRA_GROUP_ID].
     */
    private fun contentIntent(notification: PamojaNotification): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // Deliberately NOT FLAG_ACTIVITY_CLEAR_TASK, that wiped the user's
            // existing back stack every time they tapped a notification.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            notification.groupId?.let { putExtra(EXTRA_GROUP_ID, it) }
        }

        return PendingIntent.getActivity(
            context,
            // Distinct request codes so each notification keeps its own extras
            // rather than overwriting the previous PendingIntent.
            notification.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_GROUP_ID = "com.pamoja.app.extra.GROUP_ID"
    }
}
