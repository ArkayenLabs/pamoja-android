package com.pamoja.app.data.remote.firebase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.SponsorshipFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FirebaseGroupSponsorshipRepositoryImplTest {

    @Test
    fun `valid server response confirms the requested group and lease`() {
        val result = parseSponsorship(
            payload = mapOf(
                "groupId" to GROUP_ID,
                "isPremium" to true,
                "leaseValidUntilMs" to 1_800_000_000_000L,
            ),
            expectedGroupId = GROUP_ID,
        )

        assertEquals(GROUP_ID, result.groupId)
        assertEquals(1_800_000_000_000L, result.leaseValidUntilMillis)
    }

    @Test
    fun `response for another group is rejected`() {
        val error = assertThrows(AppError.Unknown::class.java) {
            parseSponsorship(
                payload = mapOf(
                    "groupId" to OTHER_GROUP_ID,
                    "isPremium" to true,
                    "leaseValidUntilMs" to 1_800_000_000_000L,
                ),
                expectedGroupId = GROUP_ID,
            )
        }

        assertTrue(error.technicalMessage.contains("different group"))
    }

    @Test
    fun `false premium or malformed lease is never accepted as success`() {
        assertThrows(AppError.Unknown::class.java) {
            parseSponsorship(
                payload = mapOf(
                    "groupId" to GROUP_ID,
                    "isPremium" to false,
                    "leaseValidUntilMs" to 1_800_000_000_000L,
                ),
                expectedGroupId = GROUP_ID,
            )
        }
        assertThrows(AppError.Unknown::class.java) {
            parseSponsorship(
                payload = mapOf(
                    "groupId" to GROUP_ID,
                    "isPremium" to true,
                    "leaseValidUntilMs" to -1L,
                ),
                expectedGroupId = GROUP_ID,
            )
        }
    }

    @Test
    fun `stable server reasons map to exact product failures`() {
        val cases = listOf(
            Triple("FAILED_PRECONDITION", "sponsorship_changed", SponsorshipFailure.AssignmentChanged),
            Triple(
                "INVALID_ARGUMENT",
                "invalid_group",
                SponsorshipFailure.InvalidGroup,
            ),
            Triple(
                "NOT_FOUND",
                "group_unavailable",
                SponsorshipFailure.GroupUnavailable,
            ),
            Triple(
                "PERMISSION_DENIED",
                "not_current_member",
                SponsorshipFailure.NotCurrentMember,
            ),
            Triple(
                "FAILED_PRECONDITION",
                "no_active_subscription",
                SponsorshipFailure.NoActiveSubscription,
            ),
            Triple(
                "FAILED_PRECONDITION",
                "subscription_assigned_elsewhere",
                SponsorshipFailure.SubscriptionAssignedElsewhere,
            ),
            Triple(
                "ALREADY_EXISTS",
                "group_already_sponsored",
                SponsorshipFailure.GroupAlreadySponsored,
            ),
        )

        cases.forEach { (statusName, wireReason, expected) ->
            val error = mapSponsorshipFunctionError(
                statusName = statusName,
                wireReason = wireReason,
                detail = "server detail",
            )
            assertTrue(error is AppError.Sponsorship)
            assertEquals(expected, (error as AppError.Sponsorship).reason)
        }
    }

    @Test
    fun `unknown reason falls back to status class instead of parsing message`() {
        val error = mapSponsorshipFunctionError(
            statusName = "FAILED_PRECONDITION",
            wireReason = "future_reason",
            detail = "Any wording can change",
        )

        assertTrue(error is AppError.Conflict)
    }

    @Test
    fun `function outage remains retryable`() {
        val error = mapSponsorshipFunctionError(
            statusName = "UNAVAILABLE",
            wireReason = null,
            detail = "temporarily unavailable",
        )

        assertTrue(error is AppError.Network)
        assertTrue(error.isRetryable)
    }

    private companion object {
        const val GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817"
        const val OTHER_GROUP_ID = "90c57875-d29c-4bb3-a3b7-d024a324f1ae"
    }
}
