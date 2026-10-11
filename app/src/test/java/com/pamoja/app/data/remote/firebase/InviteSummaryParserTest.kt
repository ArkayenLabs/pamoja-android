package com.pamoja.app.data.remote.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteSummaryParserTest {

    private val id = "6413795d-42d0-4f2b-80df-f017a9d32817"

    @Test
    fun `a full reply becomes a summary`() {
        val summary = inviteSummaryFrom(
            id,
            mapOf(
                "name" to "Sharma Family", "memberCount" to 4, "maxMemberCap" to 10,
                "weeklyTarget" to 70_000, "full" to false, "startedBy" to "Ravi",
            ),
        )!!
        assertEquals("Sharma Family", summary.name)
        assertEquals(4, summary.memberCount)
        assertEquals(10, summary.maxMemberCap)
        assertEquals(70_000, summary.weeklyTarget)
        assertEquals(false, summary.isFull)
        assertEquals("Ravi", summary.startedBy)
        assertEquals(id, summary.groupId)
    }

    @Test
    fun `odd fields become safe defaults, and no name is no preview`() {
        val summary = inviteSummaryFrom(id, mapOf("name" to "Walkers", "memberCount" to "4", "startedBy" to " "))!!
        assertEquals(0, summary.memberCount)
        assertNull(summary.startedBy)
        assertNull(inviteSummaryFrom(id, mapOf("name" to "  ")))
        assertNull(inviteSummaryFrom(id, null))
        assertNull(inviteSummaryFrom(id, "Walkers"))
    }
}
