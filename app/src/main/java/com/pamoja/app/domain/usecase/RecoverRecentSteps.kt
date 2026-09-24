package com.pamoja.app.domain.usecase

import java.time.LocalDate

/** Re-read completed days using replacement totals, never additive deltas. */
suspend fun recoverRecentSteps(
    today: LocalDate,
    read: suspend (LocalDate) -> Long?,
    save: suspend (LocalDate, Long) -> Unit,
): Boolean {
    for (offset in 1L..7L) {
        val date = today.minusDays(offset)
        val steps = read(date) ?: return false
        save(date, steps)
    }
    return true
}
