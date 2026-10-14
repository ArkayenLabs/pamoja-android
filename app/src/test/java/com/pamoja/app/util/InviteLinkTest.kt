package com.pamoja.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The invite parser is the boundary between any web page or app on the phone
 * and a navigation route plus a value saved in DataStore. August 2026's fix
 * refused only `/`; anything else a link can carry got through.
 */
class InviteLinkTest {

    private val groupId = "6413795d-42d0-4f2b-80df-f017a9d32817"

    @Test
    fun realLinksStillParse() {
        assertEquals(groupId, InviteLink.parseCode("https://www.arkayenlabs.com/pamoja/join/$groupId"))
        assertEquals(groupId, InviteLink.parseCode("  www.arkayenlabs.com/pamoja/join/$groupId/?utm_source=x  "))
        assertEquals(groupId, InviteLink.parseCode("https://arkayenlabs.com/pamoja/join/$groupId#top"))
        assertEquals(groupId, InviteLink.parseCode("pamoja://join/$groupId"))
        assertEquals("short_Code-9", InviteLink.parseCode("pamoja://join/short_Code-9"))
        assertEquals(groupId, InviteLink.parseCode(InviteLink.build(groupId)))
    }

    @Test
    fun anythingThatIsNotCodeShapedIsRefused() {
        for (hostile in listOf(
            "pamoja://join/a/b",
            "pamoja://join/a b",
            "pamoja://join/a%2Fb",
            "pamoja://join/x%3Fauto=true",
            "pamoja://join/a&auto=true",
            "pamoja://join/{code}",
            "pamoja://join/..",
            "pamoja://join/" + "a".repeat(65),
            "https://www.arkayenlabs.com/pamoja/join/a%20b",
            "https://www.arkayenlabs.com/pamoja/join/a/b",
            "https://www.arkayenlabs.com/pamoja/join/",
            "https://evil.example/pamoja/join/$groupId",
            "https://www.arkayenlabs.com/other/$groupId",
        )) {
            assertNull("$hostile must not parse", InviteLink.parseCode(hostile))
        }
    }

    @Test
    fun codeShapeIsLettersDigitsDashAndUnderscore() {
        assertEquals(true, InviteLink.isCodeShaped(groupId))
        assertEquals(false, InviteLink.isCodeShaped(""))
        assertEquals(false, InviteLink.isCodeShaped("a b"))
        assertEquals(false, InviteLink.isCodeShaped("a‮b"))
        assertEquals(false, InviteLink.isCodeShaped("é"))
    }
}
