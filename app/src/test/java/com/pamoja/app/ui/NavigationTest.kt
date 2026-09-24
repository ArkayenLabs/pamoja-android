package com.pamoja.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NavigationTest {

    @Test
    fun `paywall route always carries its group`() {
        assertEquals(
            "group/$GROUP_ID/sponsor",
            Screen.Paywall.createRoute(GROUP_ID),
        )
    }

    @Test
    fun `paywall route refuses a missing or malformed group`() {
        try {
            Screen.Paywall.createRoute("not-a-group")
            fail("Malformed group ID should not produce a paywall route")
        } catch (_: IllegalArgumentException) {
            // Expected: no group means there is nothing truthful to sell.
        }
    }

    private companion object {
        const val GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817"
    }
}
