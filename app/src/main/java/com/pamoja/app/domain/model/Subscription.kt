package com.pamoja.app.domain.model

/** What the group is entitled to. Two tiers, deliberately. */
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
 * The tier model, defined once.
 *
 * Once, because the paywall's copy and the gates that enforce it must never be
 * able to disagree: a paywall promising 20 members next to a slider that stops
 * at 15 is worse than no paywall.
 *
 * 🔴 **Group size is not sold, and must not become sold again.** This object
 * previously gated free groups at 8 members and premium at 20, and
 * `PaywallScreen` advertised exactly that. Three independent research passes on
 * 2026-08-21 reached the same verdict: charging for group size is the weakest
 * option available. Nobody upgrades to add a 21st member, size is not a felt
 * benefit, and gating it throttles the invite loop at precisely the moment a
 * group is catching on. Growth here *is* people inviting people; putting a wall
 * inside the growth loop is charging for the thing that makes the product work.
 *
 * The same reasoning removed the "one created group on free" gate. Creating,
 * joining, inviting, the weekly cycle, the ring and the leaderboard are the
 * loop, and the loop stays free on every tier, permanently.
 *
 * **Premium is a property of a GROUP, not of a person.** One member pays and
 * every member of that group gets it, the Life360 Circle model, which reported
 * roughly 3.2 million paying Circles at about $142 per year each in Q2 2026 on
 * exactly this mechanic. It converts better because only one person has to say
 * yes, it is fairer because the organiser is usually the most motivated, and it
 * is simpler to implement: one purchase token, one server-side entitlement
 * fanned out to the group.
 *
 * What premium sells is **depth**: history beyond the current week, the weekly
 * recap, streak insurance, group memory, captain tools. Depth only matters to a
 * group that is already engaged, which is exactly where willingness to pay sits.
 *
 * 🔴 **None of those features exist yet.** Do not wire an entry point to
 * `PaywallScreen` until they do. Selling a feature that has not been built is
 * both a Play policy problem and a refund conversation.
 */
object PlanLimits {

    /**
     * The size a group is nudged towards at creation. Not enforced, and not a
     * paywall: a suggestion.
     *
     * Small on purpose. Social loafing rises with group size and individual
     * contribution becomes less visible as a group grows, which is the
     * mechanism that kills step challenges by week two. Comparable products
     * land in the same place: Habitica recommends about 6, Duolingo Friend
     * Streaks allow 5, Apple Watch competitions are 1 to 1. Dunbar's inner
     * layer is about 5 and Hackman's preferred team size is about 6.
     */
    val RECOMMENDED_GROUP_SIZE = 5..8

    /**
     * Whether a group is premium.
     *
     * Takes the group's entitlement rather than the caller's, because premium
     * belongs to the group. A member who has never paid anything is premium
     * inside a group somebody else pays for, and free in a group nobody does.
     */
    fun isGroupPremium(groupEntitlement: Entitlement): Boolean =
        groupEntitlement.isPremium

    /**
     * What a lapse does, stated as code so nobody has to remember it.
     *
     * Never remove members, never delete history, never stop tracking steps.
     * The group drops to the free feature set and keeps working, and any member
     * can take over paying. Innocent members losing access because somebody
     * else's card expired is how family-plan products earn one-star reviews.
     */
    const val LAPSE_REMOVES_MEMBERS = false
    const val LAPSE_DELETES_HISTORY = false
    const val LAPSE_STOPS_TRACKING = false
}
