package com.pamoja.app.data.repository

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.Entitlement
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everyone is on the free plan and nothing is for sale.
 *
 * A stand-in until RevenueCat can be wired, which needs a dashboard, an API key
 * and Play subscription products, none of which can exist until the Play
 * merchant chain finishes. Named for what it does rather than "Stub" or "Fake",
 * so nobody reads a call site and assumes real entitlements are arriving.
 *
 * It refuses rather than pretends, on purpose:
 *
 *  - [entitlement] is always Free. It never grants Premium, not even in debug.
 *    A debug-only entitlement is exactly the kind of thing that survives into a
 *    release by accident, and the failure mode is giving away the product.
 *  - [availablePlans] returns an empty list, which is a state the paywall has
 *    to handle anyway for a store outage or a missing product. Wiring it to
 *    that state now means the case is exercised from day one rather than being
 *    written blind and first executed in front of a user.
 *
 * Replace wholesale. Nothing here is meant to survive.
 */
@Singleton
class FreeOnlySubscriptionRepository @Inject constructor() : SubscriptionRepository {

    override val entitlement: Flow<Entitlement> = flowOf(Entitlement())

    override suspend fun availablePlans(): Result<List<SubscriptionPlan>> =
        Result.success(emptyList())

    override suspend fun purchase(planId: String, activity: Any): Result<Entitlement> =
        Result.failure(AppError.NotFound("No billing provider is configured yet"))

    override suspend fun restorePurchases(): Result<Entitlement> =
        Result.failure(AppError.NotFound("No billing provider is configured yet"))
}
