package com.pamoja.app.data.export

import com.pamoja.app.domain.model.Signal
import com.pamoja.app.domain.model.SignalKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportedSignalRowsTest {

    @Test
    fun `rows are from your side, in the words people see, oldest first`() {
        val rows = exportedSignalRows(
            userId = "me",
            signals = mapOf(
                "g1" to listOf(
                    Signal(SignalKind.NUDGE, fromUserId = "priya", toUserId = "me", date = "2026-10-09"),
                    Signal(SignalKind.CHEER, fromUserId = "me", toUserId = "ravi", date = "2026-10-08"),
                ),
            ),
        )
        assertEquals(
            listOf(
                ExportedSignalRow("g1", "high_five", "sent", "ravi", "2026-10-08"),
                ExportedSignalRow("g1", "reminder", "received", "priya", "2026-10-09"),
            ),
            rows,
        )
    }

    @Test
    fun `nothing sent or received is an empty list`() {
        assertEquals(emptyList<ExportedSignalRow>(), exportedSignalRows("me", emptyMap()))
    }
}
