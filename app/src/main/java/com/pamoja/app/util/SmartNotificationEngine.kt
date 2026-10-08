package com.pamoja.app.util

import java.time.LocalTime

// ============================================================================
// PAMOJA NOTIFICATION ENGINE (on this phone)
// ============================================================================
// Pure Kotlin. No Android imports, so it is unit testable in isolation.
//
// Since 2026-10 this decides one thing only: the confirmation after the first
// step sync, which needs the phone because only the phone knows it happened.
// Everything else is sent by functions-notifications (NOTIFICATIONS_PLAN.md):
// pushes when a person does something, and the scheduled reminder and recap,
// which used to live here and arrived late or never on phones that hold back
// background work.
//
// Deleted on purpose, do not bring back: "X passed you", "you took first",
// "X steps behind Y" (rank pings turn friends into opponents in a product
// built on one shared goal) and the low-step roasts (they fail the hard-day
// test: would this land badly on someone who is ill or having a bad week?).
//
// The words are string resources (values/strings_notifications.xml), rendered
// by NotificationCopy. This file decides what to say, never how.
// ============================================================================

/** Categories map to separate Android channels so muting reminders does not
 *  also mute the moments that matter. */
enum class NotificationCategory(val channelId: String) {
    ACHIEVEMENT("pamoja_achievements"),
    GROUP_ACTIVITY("pamoja_group_activity"),
    REMINDER("pamoja_reminders"),
    RECAP("pamoja_recap"),
}

/**
 * Minutes from midnight to a wall-clock time.
 *
 * Quiet hours are stored as an Int because a formatted string would break the
 * moment the device locale or timezone changed. Coerced rather than trusted, so
 * a corrupt value cannot throw inside the notification path.
 */
fun minuteOfDayToTime(minuteOfDay: Int): LocalTime {
    val safe = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return LocalTime.of(safe / 60, safe % 60)
}

/**
 * Handles a window that wraps past midnight, which the usual one is.
 *
 * 22:00 to 08:00 means "after 22:00 OR before 08:00", but a user who sets
 * 09:00 to 17:00 means "after 09:00 AND before 17:00". Treating both the
 * same way silences the app all day for anyone who picks a daytime window.
 */
fun isQuietHours(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
    if (start <= end) {
        now >= start && now < end
    } else {
        now >= start || now < end
    }

/** A notification ready to post, words already chosen. */
data class PamojaNotification(
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val priority: Int,
    val groupId: String?,
    val notificationId: Int,
)

/** What the engine decided to say, without the words. */
sealed interface LocalMessage {
    val groupName: String

    /** First sync, and today already has steps. */
    data class StepsIn(val stepsToday: Long, override val groupName: String) : LocalMessage

    /** First sync, nothing walked yet today. */
    data class AllSet(override val groupName: String) : LocalMessage
}

data class LocalNotice(
    val category: NotificationCategory,
    val message: LocalMessage,
    val groupId: String,
    val notificationId: Int,
)

data class NotificationContext(
    val groupId: String,
    val groupName: String,
    val userStepsToday: Long,
    val isFirstEverSync: Boolean,
    val now: LocalTime,

    /**
     * Categories the user has switched off, read from the Android channels by
     * the caller, so the engine stays pure and every gate input arrives the
     * same way.
     */
    val mutedCategories: Set<NotificationCategory> = emptySet(),

    /** User-chosen quiet window. */
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(8, 0),
)

object SmartNotificationEngine {

    private const val ID_FIRST_SYNC = 2001

    /** The one notification worth sending after this sync, or null. */
    fun select(ctx: NotificationContext): LocalNotice? {
        if (!ctx.isFirstEverSync) return null
        // A switch the user turned off is a decision, not a heuristic.
        if (NotificationCategory.ACHIEVEMENT in ctx.mutedCategories) return null
        if (isQuietHours(ctx.now, ctx.quietStart, ctx.quietEnd)) return null

        // Kills the "is this thing even on" doubt that quietly loses people in
        // week one.
        val message = if (ctx.userStepsToday > 0L) {
            LocalMessage.StepsIn(ctx.userStepsToday, ctx.groupName)
        } else {
            LocalMessage.AllSet(ctx.groupName)
        }
        return LocalNotice(NotificationCategory.ACHIEVEMENT, message, ctx.groupId, ID_FIRST_SYNC)
    }
}
