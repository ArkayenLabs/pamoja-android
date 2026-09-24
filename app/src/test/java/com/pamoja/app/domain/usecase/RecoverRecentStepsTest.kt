package com.pamoja.app.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecoverRecentStepsTest {
    private val today = LocalDate.of(2026, 9, 13)

    @Test fun `recovers seven completed days and repeated runs replace totals`() = runTest {
        val saved = mutableMapOf<LocalDate, Long>()
        repeat(2) {
            assertTrue(recoverRecentSteps(today, { 123L }, { day, count -> saved[day] = count }))
        }
        assertEquals((1L..7L).map { today.minusDays(it) }.toSet(), saved.keys)
        assertEquals(861L, saved.values.sum())
        assertFalse(saved.containsKey(today))
    }

    @Test fun `unavailable reading never becomes zero or completes recovery`() = runTest {
        val saved = mutableMapOf<LocalDate, Long>()
        assertFalse(recoverRecentSteps(today, { null }, { day, count -> saved[day] = count }))
        assertTrue(saved.isEmpty())
    }

    @Test fun `confirmed zero is saved but write failures are propagated`() = runTest {
        var writes = 0
        try {
            recoverRecentSteps(today, { 0L }, { _, count ->
                assertEquals(0L, count)
                writes++
                throw IllegalStateException("offline")
            })
            fail("Failed write must not mark recovery complete")
        } catch (_: IllegalStateException) { }
        assertEquals(1, writes)
    }
}
