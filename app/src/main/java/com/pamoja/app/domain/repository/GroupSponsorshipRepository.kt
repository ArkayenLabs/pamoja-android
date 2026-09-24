package com.pamoja.app.domain.repository

/**
 * The server-confirmed access created when one member sponsors a group.
 * A store tier may give the payer capacity to sponsor more than one group.
 *
 * A successful store purchase is not enough to construct this value. Only the
 * backend can verify the entitlement, recheck membership and return the access
 * lease that every member of the chosen group may use.
 */
data class GroupSponsorship(
    val groupId: String,
    val leaseValidUntilMillis: Long,
)

/** Server-confirmed checkout context. Only the payer may read this information. */
data class PurchaseContext(
    val groupId: String,
    val payerUid: String,
    val groupIsPremium: Boolean,
    val isGroupSponsor: Boolean,
    val subscriptionIsActive: Boolean,
    val sponsoredGroupId: String?,
    val sponsoredGroupName: String?,
    val planningAvailable: Boolean = false,
    val trailAvailable: Boolean = false,
    val groupCapacity: Int = if (subscriptionIsActive) 1 else 0,
    val sponsoredGroupIds: List<String> = listOfNotNull(sponsoredGroupId),
) {
    val assignedElsewhere: Boolean
        get() = sponsoredGroupIds.isNotEmpty() && groupId !in sponsoredGroupIds
    val hasAvailableGroupSlot: Boolean
        get() = subscriptionIsActive && sponsoredGroupIds.size < groupCapacity
}

interface GroupSponsorshipRepository {
    suspend fun purchaseContext(groupId: String): Result<PurchaseContext> =
        Result.failure(IllegalStateException("Purchase context unavailable"))

    suspend fun activateChecked(context: PurchaseContext, replaceExisting: Boolean): Result<GroupSponsorship> =
        Result.failure(IllegalStateException("Checked activation unavailable"))

    /**
     * Assigns the signed-in user's already-active entitlement to [groupId].
     *
     * The operation is safe to repeat for the same payer and group. It must not
     * be retried automatically for a different group because moving a payment
     * is a user decision, not transport recovery.
     */
    suspend fun activate(
        groupId: String,
        replaceExisting: Boolean = false,
    ): Result<GroupSponsorship>
}
