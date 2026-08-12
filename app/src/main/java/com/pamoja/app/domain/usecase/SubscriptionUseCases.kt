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
 * The largest member cap this account may set on a group it creates.
 *
 * A flow rather than a value, because a purchase must widen the slider on a
 * screen that is already open.
 */
class ObserveMemberCapLimitUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) {
    operator fun invoke(): Flow<Int> =
        subscriptionRepository.entitlement.map { PlanLimits.memberCapFor(it) }
}

/**
 * Whether another group may be created.
 *
 * Counts only groups this user *administers*, never groups they joined.
 * Joining is unlimited on every plan and always will be: an invitation that
 * bounces because the invitee is at a cap breaks the one loop this product
 * depends on, and punishes the wrong person entirely, since the one who paid
 * is the person whose group just lost a member.
 */
class ObserveCanCreateGroupUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val groupRepository: GroupRepository,
) {
    operator fun invoke(userId: String): Flow<Boolean> = combine(
        subscriptionRepository.entitlement,
        groupRepository.getUserGroups(userId),
    ) { entitlement, groups ->
        val created = groups.count { it.adminId == userId }
        PlanLimits.canCreateGroup(entitlement, created)
    }
}
