package com.pamoja.app.domain.model

/** What the account is entitled to. Two tiers, deliberately. */
enum class PlanTier { Free, Premium }

/** How a Premium plan is billed. Prices come from the store, never from here. */
enum class BillingPeriod { Monthly, Annual }

/**
 * What this account can do right now.
 *
 * Deliberately a *state*, not a receipt: nothing here says how the entitlement
 * was obtained. That keeps the gates below indifferent to whether Premium came
 * from a purchase, a trial, a promo code or a licence tester, which is what
 * stops a judge with a promo code hitting a paywall the paying user does not.
 */
data class Entitlement(
    val tier: PlanTier = PlanTier.Free,
    /** True while a free trial is running, so copy can say so honestly. */
    val isInTrial: Boolean = false,
    /** Epoch millis this period ends. 0 when free. */
    val expiresAt: Long = 0L,
) {
    val isPremium: Boolean get() = tier == PlanTier.Premium
}

/**
 * The tier limits, defined once.
 *
 * Once, because the paywall's copy and the gates that enforce it must never be
 * able to disagree: a paywall promising 20 members next to a slider that stops
 * at 15 is worse than no paywall. The same lesson as the submit buttons that
 * did not ask the validation rules they displayed.
 *
 * These describe what you may *newly do*. They deliberately say nothing about
 * existing groups, which is what makes the "nothing is deleted, nobody is
 * removed" promise in the Terms hold: a group's cap lives on the group document
 * and is enforced by the join transaction, so a group created under Premium
 * keeps working untouched if the owner's subscription lapses.
 */
object PlanLimits {

    /** Groups you can *create* on free. Joining is never limited, on any plan. */
    const val FREE_CREATED_GROUPS = 1

    const val FREE_MEMBER_CAP = 8
    const val PREMIUM_MEMBER_CAP = 20

    /** The largest member cap this entitlement may set on a new group. */
    fun memberCapFor(entitlement: Entitlement): Int =
        if (entitlement.isPremium) PREMIUM_MEMBER_CAP else FREE_MEMBER_CAP

    /**
     * Whether another group may be created, given how many this user already
     * administers. Premium is unlimited.
     */
    fun canCreateGroup(entitlement: Entitlement, groupsCreated: Int): Boolean =
        entitlement.isPremium || groupsCreated < FREE_CREATED_GROUPS
}
