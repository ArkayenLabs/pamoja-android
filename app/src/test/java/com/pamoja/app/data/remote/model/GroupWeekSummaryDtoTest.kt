package com.pamoja.app.data.remote.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupWeekSummaryDtoTest {

    @Test
    fun validSummaryMapsWithoutProfileDataAndComputesProgress() {
        val summary = GroupWeekSummaryDto(
            schemaVersion = 1,
            groupId = "6413795d-42d0-4f2b-80df-f017a9d32817",
            weekStart = "2026-08-17",
            weekEnd = "2026-08-23",
            targetSteps = 70_000L,
            totalSteps = 84_000L,
            memberCount = 2,
            activeMemberCount = 2,
            contributions = listOf(
                GroupWeekContributionDto("member-a", 30_000L),
                GroupWeekContributionDto("member-b", 54_000L),
            ),
            finalizedAt = Timestamp(1_700_000_000L, 0),
        ).toDomain()

        requireNotNull(summary)
        assertTrue(summary.goalHit)
        assertEquals(1f, summary.completionFraction)
        assertEquals(2, summary.contributions.size)
        assertEquals(1_700_000_000_000L, summary.finalizedAtEpochMillis)
    }

    @Test
    fun malformedAndDuplicateContributionsAreMadeSafe() {
        val summary = GroupWeekSummaryDto(
            schemaVersion = 1,
            groupId = "group",
            weekStart = "2026-08-17",
            weekEnd = "2026-08-23",
            targetSteps = 100L,
            totalSteps = 50L,
            memberCount = 1,
            activeMemberCount = 99,
            contributions = listOf(
                GroupWeekContributionDto("member-a", -1L),
                GroupWeekContributionDto("member-a", 500L),
                GroupWeekContributionDto("bad/member", 500L),
            ),
        ).toDomain()

        requireNotNull(summary)
        assertFalse(summary.goalHit)
        assertEquals(1, summary.memberCount)
        assertEquals(1, summary.activeMemberCount)
        assertEquals(listOf(0L), summary.contributions.map { it.stepCount })
    }

    @Test
    fun unknownSchemaOrInvalidDatesAreNotRendered() {
        assertNull(
            GroupWeekSummaryDto(
                schemaVersion = 2,
                groupId = "group",
                weekStart = "2026-08-17",
                weekEnd = "2026-08-23",
            ).toDomain(),
        )
        assertNull(
            GroupWeekSummaryDto(
                schemaVersion = 1,
                groupId = "group",
                weekStart = "2026-02-30",
                weekEnd = "2026-03-08",
            ).toDomain(),
        )
    }
}
