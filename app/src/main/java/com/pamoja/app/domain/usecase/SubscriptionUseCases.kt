package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.Entitlement
import com.pamoja.app.domain.model.PlanLimits
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveEntitlementUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) {
    operator fun invoke(): Flow<Entitlement> = subscriptionRepository.entitlement
}

class GetSubscriptionPlansUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend operator fun invoke(): Result<List<SubscriptionPlan>> =
        subscriptionRepository.availablePlans()
}

class PurchaseSubscriptionUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend operator fun invoke(planId: String, activity: Any): Result<Entitlement> =
        subscriptionRepository.purchase(planId, activity)
}

class RestorePurchasesUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend operator fun invoke(): Result<Entitlement> =
        subscriptionRepository.restorePurchases()
}

/**
 * Group size and group creation are deliberately NOT gated.
 *
 * `ObserveMemberCapLimitUseCase` and `ObserveCanCreateGroupUseCase` used to
 * live here and were deleted on 2026-08-21 along with the size paywall they
 * enforced. See PlanLimits for why: charging for group size throttles the one
 * loop this product grows through. If a future change reintroduces a cap that
 * differs by tier, it is reintroducing the model that was measured and rejected.
 */
