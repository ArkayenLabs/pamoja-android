package com.pamoja.app.notifications

import com.pamoja.app.util.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteNotificationParserTest {

    private val validData = mapOf(
        "schemaVersion" to "1",
        "recipientUid" to "firebase-user-1",
        "eventId" to "member-joined-123",
        "category" to "GROUP_ACTIVITY",
        "title" to "Asha joined",
        "body" to "The group just grew.",
        "groupId" to "6413795d-42d0-4f2b-80df-f017a9d32817",
    )

    @Test
    fun validPayloadBecomesADeepLinkedNotification() {
        val remote = requireNotNull(RemoteNotificationParser.parse(validData))

        assertEquals("firebase-user-1", remote.recipientUid)
        assertEquals("member-joined-123", remote.eventId)
        assertEquals(NotificationCategory.GROUP_ACTIVITY, remote.notification.category)
        assertEquals(validData["groupId"], remote.notification.groupId)
        assertTrue(remote.notification.notificationId > 0)
    }

    @Test
    fun eventIdProducesAStableButDistinctTrayId() {
        val first = requireNotNull(RemoteNotificationParser.parse(validData))
        val retry = requireNotNull(RemoteNotificationParser.parse(validData))
        val other = requireNotNull(
            RemoteNotificationParser.parse(validData + ("eventId" to "member-joined-456"))
        )

        assertEquals(first.notification.notificationId, retry.notification.notificationId)
        assertNotEquals(first.notification.notificationId, other.notification.notificationId)
    }

    @Test
    fun malformedOrUnexpectedPayloadsAreRejected() {
        val invalidVariants = listOf(
            validData + ("schemaVersion" to "2"),
            validData + ("category" to "ADMIN_MESSAGE"),
            validData + ("groupId" to "guessable-group"),
            validData + ("title" to "x".repeat(41)),
            validData + ("body" to "x".repeat(121)),
            validData - "recipientUid",
        )

        invalidVariants.forEach { assertNull(RemoteNotificationParser.parse(it)) }
    }
}
