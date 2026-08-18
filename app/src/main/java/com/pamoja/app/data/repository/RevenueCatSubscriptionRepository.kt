package com.pamoja.app.data.repository

import android.app.Activity
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.model.Entitlement
import com.pamoja.app.domain.model.PlanTier
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.domain.repository.SubscriptionRepository
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entitlements from RevenueCat.
 *
 * Replaces `FreeOnlySubscriptionRepository` and absorbs its job: when the SDK
 * has not been configured, which is the case until the Play merchant chain
 * finishes and an API key exists, every method here behaves exactly as that
 * stand-in did. Free tier, nothing for sale, purchases refused.
 *
 * That is why there is one implementation rather than two behind a flag. A
 * build with a key and a build without differ in what RevenueCat returns, not
 * in which class is bound, so the unconfigured path is the same code the
 * configured path runs and cannot rot separately.
 *
 * **It never grants Premium as a fallback.** Every failure resolves to Free.
 * The tempting shortcut, treating "cannot reach the store" as "let them in", is
 * how a subscription app gives itself away to anyone who turns off wifi. The
 * opposite failure, briefly showing a paying user a paywall during an outage,
 * is recoverable and much rarer.
 */
@Singleton
class RevenueCatSubscriptionRepository @Inject constructor() : SubscriptionRepository {

    /**
     * The live entitlement.
     *
     * `callbackFlow` over RevenueCat's listener, matching how Firestore streams
     * are exposed elsewhere in this project. Seeded with a fetch, because the
     * listener only fires on *changes* and a screen opened before the first
     * change would otherwise sit on an empty flow forever.
     */
    override val entitlement: Flow<Entitlement> =
        if (!Purchases.isConfigured) {
            flowOf(Entitlement())
        } else {
            callbackFlow {
                val listener = UpdatedCustomerInfoListener { info ->
                    trySend(info.toEntitlement())
                }

                // Seeded first, so the flow has a value immediately.
                runCatching { Purchases.sharedInstance.awaitCustomerInfo() }
                    .onSuccess { trySend(it.toEntitlement()) }
                    .onFailure { trySend(Entitlement()) }

                Purchases.sharedInstance.updatedCustomerInfoListener = listener

                awaitClose {
                    // Cleared rather than replaced with a no-op: the SDK holds a
                    // single listener, and leaving ours attached would keep this
                    // flow's scope reachable.
                    Purchases.sharedInstance.updatedCustomerInfoListener = null
                }
            }
        }

    override suspend fun availablePlans(): Result<List<SubscriptionPlan>> {
        if (!Purchases.isConfigured) return Result.success(emptyList())

        return runCatching {
            val offering = Purchases.sharedInstance.awaitOfferings().current
                ?: return Result.success(emptyList())

            offering.availablePackages.mapNotNull { it.toPlan() }
        }.recoverCatching { error ->
            // An empty list is a state the paywall already renders as "plans
            // unavailable". Surfacing a failure instead would put an error
            // screen in front of someone over a store hiccup they cannot act on.
            if (error is PurchasesException) emptyList() else throw error
        }
    }

    override suspend fun purchase(planId: String, activity: Any): Result<Entitlement> {
        if (!Purchases.isConfigured) {
            return Result.failure(AppError.NotFound("No billing provider is configured yet"))
        }
        val hostActivity = activity as? Activity
            ?: return Result.failure(AppError.Unknown("Purchase needs an Activity"))

        return runCatching {
            val offering = Purchases.sharedInstance.awaitOfferings().current
                ?: throw AppError.NotFound("No offering is configured")

            val target = offering.availablePackages.firstOrNull {
                it.product.id == planId || it.identifier == planId
            } ?: throw AppError.NotFound("That plan is no longer available")

            Purchases.sharedInstance
                .awaitPurchase(PurchaseParams.Builder(hostActivity, target).build())
                .customerInfo
                .toEntitlement()
        }.recoverCatching { error -> throw error.toBillingAppError() }
    }

    override suspend fun restorePurchases(): Result<Entitlement> {
        if (!Purchases.isConfigured) {
            return Result.failure(AppError.NotFound("No billing provider is configured yet"))
        }

        return runCatching {
            Purchases.sharedInstance.awaitRestore().toEntitlement()
        }.recoverCatching { error -> throw error.toBillingAppError() }
    }

    companion object {
        /**
         * The entitlement identifier configured in the RevenueCat dashboard.
         *
         * A dashboard value, so it must match exactly and cannot be inferred.
         * If products are wired up under a different name, entitlements resolve
         * to Free forever with no error anywhere. This constant exists so that
         * failure is greppable rather than mysterious.
         */
        const val PREMIUM_ENTITLEMENT = "premium"
    }
}

/**
 * RevenueCat's customer state, as ours.
 *
 * Reads the entitlement rather than the list of purchases, deliberately. An
 * entitlement is already the answer to "what may this account do", and it is
 * true for a promo code and a licence tester as much as for a payer, which is
 * what keeps a judge holding a promo code out of the paywall.
 */
private fun CustomerInfo.toEntitlement(): Entitlement {
    val premium = entitlements.active[
        RevenueCatSubscriptionRepository.PREMIUM_ENTITLEMENT
    ] ?: return Entitlement()

    return Entitlement(
        tier = PlanTier.Premium,
        // Compared by name rather than against an enum constant, so a case
        // added by an SDK update cannot fail to compile here and quietly change
        // how trials are reported.
        isInTrial = premium.periodType.name.equals("TRIAL", ignoreCase = true),
        expiresAt = premium.expirationDate?.time ?: 0L,
    )
}

/**
 * One RevenueCat package as a plan.
 *
 * Returns null for anything that is neither monthly nor annual. The tier table
 * has exactly two, and surfacing a lifetime or weekly product the Terms never
 * described would be a pricing change nobody approved.
 */
private fun Package.toPlan(): SubscriptionPlan? {
    val period = when (packageType) {
        PackageType.MONTHLY -> BillingPeriod.Monthly
        PackageType.ANNUAL -> BillingPeriod.Annual
        else -> return null
    }

    val freePhase = product.defaultOption?.freePhase

    return SubscriptionPlan(
        id = product.id,
        period = period,
        // The store's own formatted string, already localised. Never formatted
        // here: Play sets prices per country and changes them without a release.
        price = product.price.formatted,
        trialDays = freePhase?.billingPeriod?.let { period ->
            when (period.unit.name.uppercase()) {
                "DAY" -> period.value
                "WEEK" -> period.value * 7
                "MONTH" -> period.value * 30
                "YEAR" -> period.value * 365
                else -> 0
            }
        } ?: 0,
    )
}

/**
 * Billing failures as domain errors.
 *
 * Matched on RevenueCat's error code rather than its message, for the same
 * reason the Firebase mapper is: messages are not API and change between
 * versions. Cancellation matters most, because it is not a failure at all and
 * must never reach the user as one.
 */
private fun Throwable.toBillingAppError(): Throwable {
    if (this is AppError) return this

    val error = (this as? PurchasesException)?.error
        ?: (this as? PurchasesError)
        ?: return AppError.Unknown(message ?: "Something went wrong with the purchase")

    // The SDK reports cancellation both as a flag on the transaction exception
    // and as an error code, and only the flag is set for some store paths.
    // Checked first so a user who simply backed out is never told something
    // failed.
    if ((this as? PurchasesTransactionException)?.userCancelled == true) {
        return AppError.Conflict("Purchase cancelled")
    }

    return when (error.code) {
        PurchasesErrorCode.PurchaseCancelledError ->
            AppError.Conflict("Purchase cancelled")

        PurchasesErrorCode.NetworkError ->
            AppError.Offline()

        PurchasesErrorCode.PurchaseNotAllowedError,
        PurchasesErrorCode.PaymentPendingError ->
            AppError.PermissionDenied()

        PurchasesErrorCode.ProductNotAvailableForPurchaseError,
        PurchasesErrorCode.ProductAlreadyPurchasedError ->
            AppError.Conflict(error.message)

        else -> AppError.Unknown(error.message)
    }
}
