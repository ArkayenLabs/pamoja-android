package com.pamoja.app.domain.model

/**
 * A notification this device has already shown.
 *
 * The tray is not a record. Android clears it on reboot, a swipe destroys the
 * message, and a notification seen on a locked screen and dismissed with the
 * rest of the morning's is gone with no way back. Someone whose group hit its
 * weekly goal while they were asleep had no way to find that out afterwards,
 * which is the one message this product exists to deliver.
 *
 * Deliberately a local record rather than a Firestore collection. These are
 * generated on this device by `SmartNotificationEngine` from data the app
 * already holds, so writing them to the server would be paying per document to
 * store a copy of something derived. It also means the log does not survive a
 * reinstall, which is the correct trade: a catch-up list is worth nothing a
 * month later.
 */
data class ActivityItem(
    /** Stable id, so marking one read does not depend on list position. */
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    /** Epoch millis it was shown. */
    val shownAt: Long,
    /** Set when the notification was about a specific group, for the tap target. */
    val groupId: String?,
    val isRead: Boolean,
)

/**
 * Which channel a notification belongs to.
 *
 * Mirrors the engine's own categories. Declared in the domain layer so
 * [ActivityItem] does not have to reach into `util` for a type, and so the
 * activity log can be reasoned about without the notification machinery.
 */
enum class NotificationCategory {
    ACHIEVEMENT,
    GROUP_ACTIVITY,
    REMINDER,
    RECAP;

    companion object {
        /**
         * Unknown names fall back to group activity rather than being dropped.
         *
         * A category added in a later release and read by an older build should
         * still show the message, since losing it entirely is worse than
         * filing it under the wrong heading.
         */
        fun fromName(name: String?): NotificationCategory =
            entries.firstOrNull { it.name == name } ?: GROUP_ACTIVITY
    }
}
