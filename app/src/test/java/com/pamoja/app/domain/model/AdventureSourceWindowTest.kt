package com.pamoja.app.domain.model

import java.time.Instant
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AdventureSourceWindowTest {
    @Test
    fun oppositeSidesOfDateLineUseTheSameWindowForTheSameInstant() {
        val east = OffsetDateTime.parse("2026-09-18T01:00:00+14:00").toInstant()
        val west = OffsetDateTime.parse("2026-09-17T01:00:00-10:00").toInstant()
        assertEquals(AdventureSourceWindow.containing(east), AdventureSourceWindow.containing(west))
        assertEquals(Instant.parse("2026-09-17T00:00:00Z"), AdventureSourceWindow.containing(east).start)
    }

    @Test
    fun midnightBelongsToTheNewWindowAndDstDoesNotStretchIt() {
        val midnight = Instant.parse("2026-11-01T00:00:00Z")
        val before = AdventureSourceWindow.containing(midnight.minusNanos(1))
        val after = AdventureSourceWindow.containing(midnight)
        assertEquals(before.endExclusive, after.start)
        assertEquals(midnight.plusSeconds(86_400), after.endExclusive)
    }
}
