package com.pamoja.app.data.remote.model

import org.junit.Assert.*
import org.junit.Test

class NextWeekPlanDtoTest {
    private val legacy = NextWeekPlanDto(
        schemaVersion = 1, groupId = "group", weekStart = "2026-09-14",
        targetSteps = 70_000, choice = "repeat", sourceWeekStart = "2026-09-07",
        basisTotalSteps = 58_000, basisTargetSteps = 70_000,
        status = "scheduled", createdBy = "organizer",
    )

    @Test fun `legacy history basis still reads unchanged`() {
        val plan = requireNotNull(legacy.toDomain())
        assertEquals("2026-09-07", plan.sourceWeekStart)
        assertEquals(58_000L, plan.basisTotalSteps)
    }

    @Test fun `day one is explicitly absent history rather than invented zero`() {
        val plan = requireNotNull(legacy.copy(
            schemaVersion = 2, sourceWeekStart = null, basisTotalSteps = null,
        ).toDomain())
        assertNull(plan.sourceWeekStart)
        assertNull(plan.basisTotalSteps)
        assertEquals(70_000L, plan.basisTargetSteps)
    }

    @Test fun `malformed and unknown versions fail closed`() {
        assertNull(legacy.copy(sourceWeekStart = null).toDomain())
        assertNull(legacy.copy(schemaVersion = 3).toDomain())
        assertNull(legacy.copy(schemaVersion = 2, sourceWeekStart = null).toDomain())
        assertNull(legacy.copy(schemaVersion = 2, sourceWeekStart = null,
            basisTotalSteps = null, basisTargetSteps = 0).toDomain())
    }
}
