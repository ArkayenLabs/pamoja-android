package com.pamoja.app.domain.usecase

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class AdventureRecoveryTest {
    private fun millis(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun `long absence is bounded to seven previous UTC days plus today`() {
        val windows = adventureRecoveryWindows(millis("2026-09-19T23:59:59Z"), millis("2026-08-01T00:00:00Z"))
        assertEquals(8, windows.size)
        assertEquals(millis("2026-09-12T00:00:00Z"), windows.first())
        assertEquals(millis("2026-09-19T00:00:00Z"), windows.last())
        assertTrue(windows.zipWithNext().all { (a, b) -> b - a == 86_400_000L })
    }
    @Test fun `fresh resume never imports earlier days`() {
        val start = millis("2026-09-19T00:00:00Z")
        assertEquals(listOf(start), adventureRecoveryWindows(start + 10000, start))
        assertTrue(adventureRecoveryWindows(start, start + 86_400_000).isEmpty())
    }
    @Test fun `UTC midnight and DST cannot change recovered day length`() {
        val baseline = millis("2026-03-28T00:00:00Z")
        val before = adventureRecoveryWindows(millis("2026-03-29T23:59:59Z"), baseline)
        val after = adventureRecoveryWindows(millis("2026-03-30T00:00:00Z"), baseline)
        assertEquals(2, before.size)
        assertEquals(3, after.size)
        assertEquals(before, after.dropLast(1))
    }
    @Test fun `invalid baseline is rejected rather than shifting the source window`() {
        assertThrows(IllegalArgumentException::class.java) { adventureRecoveryWindows(100000000, 1) }
    }
}
