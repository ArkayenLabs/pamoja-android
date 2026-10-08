package com.pamoja.app.notifications

import com.pamoja.app.domain.model.PamojaGroupId
import com.pamoja.app.util.LocalizedText
import com.pamoja.app.util.NotificationCategory
import com.pamoja.app.util.PamojaNotification

data class RemoteNotification(
    val recipientUid: String,
    val eventId: String,
    /** Words as the server sent them, in English: the fallback. */
    val notification: PamojaNotification,
    /** The same words as string resources, so the phone can say them in its language. */
    val title: LocalizedText? = null,
    val body: LocalizedText? = null,
)

/**
 * Strict boundary for untrusted FCM data payloads.
 *
 * The same fields arrive three ways: in [PamojaFirebaseMessagingService] when
 * the app is running, as the extras of the intent a tapped tray notification
 * opens, and (title and body only) on the tray notification FCM drew while the
 * app was in the background. All three go through here.
 */
object RemoteNotificationParser {
    private const val SCHEMA_VERSION = "1"
    private const val MAX_UID_LENGTH = 128
    private const val MAX_EVENT_ID_LENGTH = 200
    private const val MAX_TITLE_LENGTH = 40
    private const val MAX_BODY_LENGTH = 120
    private const val MAX_ARG_LENGTH = 60
    private const val MAX_ARGS = 2

    /** Only Pamoja's own push strings; see values/strings_notifications.xml. */
    private val LOC_KEY = Regex("^push_[a-z0-9_]{1,60}$")

    /** Every field this reads, for callers holding the data as intent extras. */
    val KEYS: List<String> = listOf(
        "schemaVersion", "recipientUid", "eventId", "category", "title", "body", "groupId",
    ) + listOf("title", "body").flatMap { field ->
        listOf("${field}LocKey") + (1..MAX_ARGS).map { "${field}LocArg$it" }
    }

    /** Prefix of the tag FCM posts a tray notification under: `p1:{groupId}:{eventId}`. */
    private const val TAG_PREFIX = "p1:"

    fun parse(data: Map<String, String>): RemoteNotification? {
        if (data["schemaVersion"] != SCHEMA_VERSION) return null

        val recipientUid = data["recipientUid"]
            ?.takeIf { it.isNotBlank() && it.length <= MAX_UID_LENGTH && '/' !in it }
            ?: return null
        val eventId = data["eventId"]?.takeIf(::isValidEventId) ?: return null
        val category = categoryOf(data["category"]) ?: return null
        val title = data["title"]?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_TITLE_LENGTH }
            ?: return null
        val body = data["body"]?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_BODY_LENGTH }
            ?: return null
        val groupId = data["groupId"]
            ?.takeIf(PamojaGroupId::isValid)
            ?: return null

        return RemoteNotification(
            recipientUid = recipientUid,
            eventId = eventId,
            notification = PamojaNotification(
                category = category,
                title = title,
                body = body,
                priority = when (category) {
                    NotificationCategory.ACHIEVEMENT -> 100
                    NotificationCategory.GROUP_ACTIVITY -> 80
                    NotificationCategory.REMINDER -> 40
                    NotificationCategory.RECAP -> 50
                },
                groupId = groupId,
                notificationId = stableNotificationId(eventId),
            ),
            title = localized(data, "title"),
            body = localized(data, "body"),
        )
    }

    /** Group and event of a tray notification FCM drew, from its tag; null if it is not one. */
    fun parseTrayTag(tag: String?): Pair<String, String>? {
        if (tag == null || !tag.startsWith(TAG_PREFIX)) return null
        val parts = tag.removePrefix(TAG_PREFIX).split(':')
        if (parts.size != 2) return null
        val (groupId, eventId) = parts
        if (!PamojaGroupId.isValid(groupId) || !isValidEventId(eventId)) return null
        return groupId to eventId
    }

    fun categoryOfChannel(channelId: String?): NotificationCategory? =
        NotificationCategory.entries.firstOrNull { it.channelId == channelId }

    /** Optional and lenient: a bad key costs only the translation, never the notification. */
    private fun localized(data: Map<String, String>, field: String): LocalizedText? {
        val key = data["${field}LocKey"]?.takeIf(LOC_KEY::matches) ?: return null
        val args = (1..MAX_ARGS)
            .map { data["${field}LocArg$it"] }
            .takeWhile { it != null }
            .map { it.orEmpty() }
        if (args.any { it.length > MAX_ARG_LENGTH }) return null
        return LocalizedText(key, args)
    }

    private fun categoryOf(name: String?): NotificationCategory? =
        NotificationCategory.entries.firstOrNull { it.name == name }

    private fun isValidEventId(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_EVENT_ID_LENGTH && '\n' !in value && ':' !in value

    private fun stableNotificationId(eventId: String): Int =
        (eventId.hashCode() and Int.MAX_VALUE).takeIf { it != 0 } ?: 1
}
