package com.pamoja.app.domain.model

import java.time.Instant
import java.time.ZoneOffset

/** Stable accounting window, independent of the member's display locale and
 * device timezone. Existing local-day step totals must never be mixed into it. */
@ConsistentCopyVisibility
data class AdventureSourceWindow private constructor(
    val start: Instant,
    val endExclusive: Instant,
) {
    companion object {
        fun containing(instant: Instant): AdventureSourceWindow {
            val start = instant.atOffset(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay().toInstant(ZoneOffset.UTC)
            return AdventureSourceWindow(start, start.plusSeconds(86_400))
        }
    }
}

/** Missing or unreadable Health Connect data is represented by absence of an
 * observation, never a fabricated successful zero. Server ingestion supplies
 * revisions and receipt timestamps separately. */
data class AdventureStepObservation(
    val window: AdventureSourceWindow,
    val observedThrough: Instant,
    val steps: Long,
)
