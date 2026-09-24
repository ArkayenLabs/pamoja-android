package com.pamoja.app.notifications

import com.pamoja.app.domain.model.PamojaGroupId
import com.pamoja.app.util.NotificationCategory
import com.pamoja.app.util.PamojaNotification

data class RemoteNotification(
    val recipientUid: String,
    val eventId: String,
    val notification: PamojaNotification,
)

/** Strict boundary for untrusted FCM data payloads. */
object RemoteNotificationParser {
    private const val SCHEMA_VERSION = "1"
    private const val MAX_UID_LENGTH = 128
    private const val MAX_EVENT_ID_LENGTH = 200
    private const val MAX_TITLE_LENGTH = 40
    private const val MAX_BODY_LENGTH = 120

    fun parse(data: Map<String, String>): RemoteNotification? {
        if (data["schemaVersion"] != SCHEMA_VERSION) return null

        val recipientUid = data["recipientUid"]
            ?.takeIf { it.isNotBlank() && it.length <= MAX_UID_LENGTH && '/' !in it }
            ?: return null
        val eventId = data["eventId"]
            ?.takeIf { it.isNotBlank() && it.length <= MAX_EVENT_ID_LENGTH && '\n' !in it }
            ?: return null
        val category = runCatching {
            NotificationCategory.valueOf(data["category"].orEmpty())
        }.getOrNull() ?: return null
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
        )
    }

    private fun stableNotificationId(eventId: String): Int =
        (eventId.hashCode() and Int.MAX_VALUE).takeIf { it != 0 } ?: 1
}
