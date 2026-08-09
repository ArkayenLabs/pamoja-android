package com.pamoja.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pamoja.app.MainActivity
import com.pamoja.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts notifications produced by [SmartNotificationEngine].
 *
 * Four separate channels, deliberately. Android lets users disable channels
 * individually, with a single channel, someone annoyed by daily nudges has to
 * mute *everything*, including the group-hit-its-goal moment that is the
 * emotional payoff of the product. Splitting them lets the reminder channel
 * absorb the annoyance while the high-value channels survive.
 */
@Singleton
class SmartNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            // Rarely muted, genuinely good news. Worth a vibration.
            channel(
                id = NotificationCategory.ACHIEVEMENT.channelId,
                name = "Goals and achievements",
                description = "When your group hits its weekly goal, and streak milestones.",
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                vibrate = true,
            ),
            // Social news from real people, relevant, so default importance.
            channel(
                id = NotificationCategory.GROUP_ACTIVITY.channelId,
                name = "Group activity",
                description = "When someone joins your group or passes you on the leaderboard.",
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                vibrate = false,
            ),
            // The category people mute. Low importance: no sound, no peeking.
            channel(
                id = NotificationCategory.REMINDER.channelId,
                name = "Step reminders",
                description = "Occasional nudges when your group is close to its goal.",
                importance = NotificationManager.IMPORTANCE_LOW,
                vibrate = false,
            ),
            channel(
                id = NotificationCategory.RECAP.channelId,
                name = "Weekly recap",
                description = "A Monday summary of how your group did last week.",
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
        id: String,
        name: String,
        description: String,
        importance: Int,
        vibrate: Boolean,
    ): NotificationChannel =
        NotificationChannel(id, name, importance).apply {
            this.description = description
            enableVibration(vibrate)
            // Health-adjacent content should not be readable from a locked screen.
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }

    /** Posts a notification. No-ops if the user has revoked the permission. */
    fun show(notification: PamojaNotification) {
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

        runCatching {
            notificationManager.notify(notification.notificationId, builder.build())
        }
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
