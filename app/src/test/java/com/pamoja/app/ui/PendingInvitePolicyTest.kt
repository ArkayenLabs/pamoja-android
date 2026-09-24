package com.pamoja.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingInvitePolicyTest {

    private val now = 2_000_000_000_000L

    @Test
    fun `a recent interrupted invite can be offered for resumption`() {
        assertTrue(PendingInvitePolicy.isFresh(now - 60_000L, now))
        assertTrue(PendingInvitePolicy.isFresh(now - PendingInvitePolicy.MAX_AGE_MILLIS, now))
    }

    @Test
    fun `an old or untimestamped invite cannot surprise a later session`() {
        assertFalse(PendingInvitePolicy.isFresh(0L, now))
        assertFalse(
            PendingInvitePolicy.isFresh(
                now - PendingInvitePolicy.MAX_AGE_MILLIS - 1L,
                now,
            )
        )
    }

    @Test
    fun `an impossible future timestamp is not treated as a fresh invite`() {
        assertFalse(PendingInvitePolicy.isFresh(now + 1L, now))
    }
}
