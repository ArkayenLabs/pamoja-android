package com.pamoja.app.data.remote.firebase

import com.google.firebase.Timestamp
import com.pamoja.app.domain.model.GroupFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseGroupAccessRepositoryImplTest {

    @Test
    fun `valid server projection maps only known capabilities`() {
        val access = parseGroupAccess(
            groupId = GROUP_ID,
            fields = mapOf(
                "isPremium" to true,
                "featureSet" to listOf("circle_v1", "future_feature"),
                "leaseValidUntil" to Timestamp(1_800_000_000L, 0),
            ),
        )

        requireNotNull(access)
        assertEquals(GROUP_ID, access.groupId)
        assertEquals(setOf(GroupFeature.CircleV1), access.featureSet)
        assertEquals(1_800_000_000_000L, access.validUntilMillis)
    }

    @Test
    fun `false premium projection maps to free`() {
        val access = parseGroupAccess(
            groupId = GROUP_ID,
            fields = mapOf(
                "isPremium" to false,
                "featureSet" to listOf("circle_v1"),
                "leaseValidUntil" to Timestamp(1_800_000_000L, 0),
            ),
        )

        assertNull(access)
    }

    @Test
    fun `malformed server projections all map to free`() {
        val malformed = listOf(
            null,
            mapOf("isPremium" to true),
            mapOf(
                "isPremium" to true,
                "featureSet" to listOf("circle_v1"),
                "leaseValidUntil" to "not-a-timestamp",
            ),
            mapOf(
                "isPremium" to true,
                "featureSet" to listOf("circle_v1", 7),
                "leaseValidUntil" to Timestamp(1_800_000_000L, 0),
            ),
            mapOf(
                "isPremium" to true,
                "featureSet" to listOf("unknown_only"),
                "leaseValidUntil" to Timestamp(1_800_000_000L, 0),
            ),
        )

        assertTrue(malformed.all { fields -> parseGroupAccess(GROUP_ID, fields) == null })
    }

    @Test
    fun `valid server preview maps its shared immutable window`() {
        val preview = parseGroupPreview(
            expectedGroupId = GROUP_ID,
            fields = previewFields(),
        )

        requireNotNull(preview)
        assertEquals(GROUP_ID, preview.groupId)
        assertEquals(setOf(GroupFeature.CircleV1), preview.featureSet)
        assertEquals("2027-01-11", preview.eligibleWeekStart)
        assertEquals(PREVIEW_START_SECONDS * 1_000L, preview.startedAtMillis)
        assertEquals(PREVIEW_END_SECONDS * 1_000L, preview.validUntilMillis)
    }

    @Test
    fun `ended preview still maps so the product can explain that it ended`() {
        val preview = parseGroupPreview(
            expectedGroupId = GROUP_ID,
            fields = previewFields(
                startedAt = Timestamp(1_700_000_000L, 0),
                validUntil = Timestamp(1_700_000_001L, 0),
            ),
        )

        requireNotNull(preview)
        assertEquals(1_700_000_001_000L, preview.validUntilMillis)
    }

    @Test
    fun `malformed or reset preview projections are refused`() {
        val malformed = listOf(
            null,
            previewFields(schemaVersion = 2),
            previewFields(groupId = "another-group"),
            previewFields(featureSet = listOf("future_feature")),
            previewFields(eligibleWeekStart = "11-01-2027"),
            previewFields(startedAt = "not-a-timestamp"),
            previewFields(validUntil = "not-a-timestamp"),
            previewFields(
                startedAt = Timestamp(PREVIEW_START_SECONDS, 0),
                validUntil = Timestamp(PREVIEW_START_SECONDS, 0),
            ),
            previewFields(
                startedAt = Timestamp(PREVIEW_START_SECONDS, 0),
                validUntil = Timestamp(PREVIEW_START_SECONDS + FIFTEEN_DAYS_SECONDS, 0),
            ),
        )

        assertTrue(malformed.all { fields -> parseGroupPreview(GROUP_ID, fields) == null })
    }

    private fun previewFields(
        schemaVersion: Any = 1,
        groupId: String = GROUP_ID,
        featureSet: List<Any> = listOf("circle_v1"),
        eligibleWeekStart: String = "2027-01-11",
        startedAt: Any = Timestamp(PREVIEW_START_SECONDS, 0),
        validUntil: Any = Timestamp(PREVIEW_END_SECONDS, 0),
    ): Map<String, Any> = mapOf(
        "schemaVersion" to schemaVersion,
        "groupId" to groupId,
        "featureSet" to featureSet,
        "eligibleWeekStart" to eligibleWeekStart,
        "startedAt" to startedAt,
        "validUntil" to validUntil,
    )

    private companion object {
        const val GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817"
        const val PREVIEW_START_SECONDS = 1_800_000_000L
        const val PREVIEW_END_SECONDS = PREVIEW_START_SECONDS + 14L * 24 * 60 * 60
        const val FIFTEEN_DAYS_SECONDS = 15L * 24 * 60 * 60
    }
}
