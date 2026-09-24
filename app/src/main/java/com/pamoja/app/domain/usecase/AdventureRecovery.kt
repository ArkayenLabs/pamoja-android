package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.AdventureSourceWindow
import java.time.Instant

/** Oldest first, including today, never before this participation's baseline.
 * Uses server time so changing the phone clock cannot widen the lookback. */
fun adventureRecoveryWindows(serverNow: Long, baselineWindowStart: Long): List<Long> {
    val today = AdventureSourceWindow.containing(Instant.ofEpochMilli(serverNow)).start.toEpochMilli()
    require(baselineWindowStart >= 0 && baselineWindowStart % DAY == 0L)
    if (baselineWindowStart > today) return emptyList()
    val start = maxOf(today - 7 * DAY, baselineWindowStart)
    return generateSequence(start) { it + DAY }.takeWhile { it <= today }.toList()
}

private const val DAY = 86_400_000L
