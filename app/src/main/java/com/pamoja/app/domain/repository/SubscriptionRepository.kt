package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.model.Entitlement
import kotlinx.coroutines.flow.Flow

enum class TrialUnit { Day, Week, Month, Year }

data class TrialPeriod(
    val value: Int,
    val unit: TrialUnit,
) {
    init {
        require(value > 0) { "Trial period must be positive" }
    }
}

/**
 * One plan the user can buy.
 *
 * [price] is the store's own formatted string, already localised and in the
 * right currency, never a number we format ourselves. Play sets prices per
 * country and they change without an app release, so any price this app
 * computed would eventually lie to somebody.
 */
data class SubscriptionPlan(
    /** RevenueCat package identifier. Unique even when base plans share a product. */
    val id: String,
    /** Google Play subscription product identifier, retained for diagnostics. */
    val storeProductId: String,
    val period: BillingPeriod,
    /** Formatted by the store, e.g. "₹999.00". Display only, never arithmetic. */
    val price: String,
    /** How many groups this product covers. One for the original plan. */
    val groupCapacity: Int = 1,
    /** Exact store trial period. Null when this package has no free phase. */
    val trial: TrialPeriod? = null,
)

/**
 * Entitlements and purchasing.
 *
 * Kept behind an interface with no billing vocabulary in it so the rest of the
 * app never learns what is behind it. That matters more than usual here: the
 * implementation will be RevenueCat, which cannot be wired until the Play
 * merchant chain completes, so everything above this line has to be buildable
 * and reviewable before the SDK exists.
 */
interface SubscriptionRepository {

    /**
     * The current entitlement, as a stream rather than a lookup.
     *
     * A purchase has to unlock every open screen at once. Polling for that, or
     * requiring a restart, is how apps end up taking someone's money and still
     * showing them a paywall.
     */
    val entitlement: Flow<Entitlement>

    /** The plans on offer, from the store. Empty when none can be loaded. */
    suspend fun availablePlans(): Result<List<SubscriptionPlan>>

    /**
     * Starts the store's purchase flow. [activity] is Any for the same reason
     * as in AuthRepository: the domain layer may not name an Android type.
     */
    suspend fun purchase(
        planId: String,
        activity: Any,
        replacingProductId: String? = null,
    ): Result<Entitlement>

    /** Re-reads entitlements from the store, for a reinstall or a new device. */
    suspend fun restorePurchases(): Result<Entitlement>
}
