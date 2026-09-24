package com.pamoja.app.ui.group

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.GroupAccess
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupFeature
import com.pamoja.app.domain.model.GroupPreview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSponsorshipEntryTest {

    @Test
    fun `sales switch keeps every sponsorship entry dormant`() {
        assertFalse(shouldShowSponsorshipEntry(false, GroupAccessState.Free))
        assertFalse(shouldShowSponsorshipEntry(false, expiredPreview()))
    }

    @Test
    fun `free and ended preview groups may open checkout when sales are enabled`() {
        assertTrue(shouldShowSponsorshipEntry(true, GroupAccessState.Free))
        assertTrue(shouldShowSponsorshipEntry(true, expiredPreview()))
    }

    @Test
    fun `active access and uncertain states never show checkout`() {
        assertFalse(shouldShowSponsorshipEntry(true, GroupAccessState.Loading))
        assertFalse(shouldShowSponsorshipEntry(true, activePreview()))
        assertFalse(shouldShowSponsorshipEntry(true, premium()))
        assertFalse(
            shouldShowSponsorshipEntry(
                true,
                GroupAccessState.Unavailable(AppError.Offline()),
            )
        )
    }

    private fun expiredPreview() = GroupAccessState.PreviewExpired(
        GroupPreview(
            groupId = GROUP_ID,
            featureSet = setOf(GroupFeature.CircleV1),
            eligibleWeekStart = "2026-09-01",
            startedAtMillis = 1_000L,
            validUntilMillis = 2_000L,
        )
    )

    private fun activePreview() = GroupAccessState.Preview(
        GroupPreview(
            groupId = GROUP_ID,
            featureSet = setOf(GroupFeature.CircleV1),
            eligibleWeekStart = "2026-09-01",
            startedAtMillis = 1_000L,
            validUntilMillis = Long.MAX_VALUE,
        )
    )

    private fun premium() = GroupAccessState.Premium(
        GroupAccess(
            groupId = GROUP_ID,
            featureSet = setOf(GroupFeature.CircleV1),
            validUntilMillis = Long.MAX_VALUE,
        )
    )

    private companion object {
        const val GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817"
    }
}
