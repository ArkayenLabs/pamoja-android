package com.pamoja.app.data.repository

import android.app.Activity
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.BillingFailure
import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.model.Entitlement
import com.pamoja.app.domain.model.PlanTier
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.domain.repository.SubscriptionRepository
import com.pamoja.app.domain.repository.TrialPeriod
import com.pamoja.app.domain.repository.TrialUnit
import com.pamoja.app.util.BillingIdentity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.models.StoreReplacementMode
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entitlements from RevenueCat.
 *
 * Replaces `FreeOnlySubscriptionRepository` and absorbs its job: when the
 * explicit sales switch is off, the SDK key is absent, or nobody is signed in,
 * every method behaves exactly as that stand-in did. Free tier, nothing for
 * sale, purchases refused.
 *
 * That is why there is one implementation rather than two. The disabled and
 * enabled paths share the same repository, so the safe Free path cannot rot
 * separately while payment work continues.
 *
 * **It never grants Premium as a fallback.** Every failure resolves to Free.
 * The tempting shortcut, treating "cannot reach the store" as "let them in", is
 * how a subscription app gives itself away to anyone who turns off wifi. The
 * opposite failure, briefly showing a paying user a paywall during an outage,
 * is recoverable and much rarer.
 */
@Singleton
class RevenueCatSubscriptionRepository @Inject constructor() : SubscriptionRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _entitlement = MutableStateFlow(Entitlement())

    init {
        repositoryScope.launch {
            BillingIdentity.sessionState.collectLatest { state ->
                if (state == BillingIdentity.SessionState.Ready && BillingIdentity.isReady) {
                    val session = BillingIdentity.sessionToken ?: return@collectLatest
                    // RevenueCat supports one customer-info listener. Installing
                    // it once in this singleton avoids collectors replacing one
                    // another when several screens observe the entitlement.
                    Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { info ->
                        if (BillingIdentity.sessionToken == session) _entitlement.value = info.toEntitlement()
                    }
                    runCatching { Purchases.sharedInstance.awaitCustomerInfo() }
                        .onSuccess { info ->
                            if (BillingIdentity.sessionToken == session) {
                                _entitlement.value = info.toEntitlement()
                            }
                        }
                } else {
                    if (Purchases.isConfigured) {
                        Purchases.sharedInstance.updatedCustomerInfoListener = null
                    }
                    _entitlement.value = Entitlement()
                }
            }
        }
    }

    /**
     * The live entitlement.
     *
     * A single StateFlow fed by RevenueCat's single customer-info listener.
     * Seeded with a fetch whenever the authenticated billing session becomes
     * ready, because the listener only fires on later changes.
     */
    override val entitlement: Flow<Entitlement> = _entitlement.asStateFlow()

    override suspend fun availablePlans(): Result<List<SubscriptionPlan>> {
        if (!BillingIdentity.isReady) return Result.success(emptyList())

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

    override suspend fun purchase(
        planId: String,
        activity: Any,
        replacingProductId: String?,
    ): Result<Entitlement> {
        if (!BillingIdentity.isReady) {
            return Result.failure(AppError.NotFound("No billing provider is configured yet"))
        }
        val hostActivity = activity as? Activity
            ?: return Result.failure(AppError.Unknown("Purchase needs an Activity"))

        val session = BillingIdentity.sessionToken ?: return Result.failure(AppError.SessionExpired())
        return runCatching {
            val offering = Purchases.sharedInstance.awaitOfferings().current
                ?: throw AppError.NotFound("No offering is configured")

            val target = offering.availablePackages.firstOrNull {
                it.identifier == planId
            } ?: throw AppError.NotFound("That plan is no longer available")

            if (BillingIdentity.sessionToken != session) throw AppError.SessionExpired()

            val purchaseParams = PurchaseParams.Builder(hostActivity, target).apply {
                if (!replacingProductId.isNullOrBlank()) {
                    oldProductId(replacingProductId)
                    replacementMode(StoreReplacementMode.CHARGE_PRORATED_PRICE)
                }
            }.build()
            val entitlement = Purchases.sharedInstance
                .awaitPurchase(purchaseParams)
                .customerInfo
                .toEntitlement()
            if (BillingIdentity.sessionToken != session) throw AppError.SessionExpired()
            _entitlement.value = entitlement
            entitlement
        }.recoverCatching { error -> throw error.toBillingAppError() }
    }

    override suspend fun restorePurchases(): Result<Entitlement> {
        if (!BillingIdentity.isReady) {
            return Result.failure(AppError.NotFound("No billing provider is configured yet"))
        }

        val session = BillingIdentity.sessionToken ?: return Result.failure(AppError.SessionExpired())
        return runCatching {
            val entitlement = Purchases.sharedInstance.awaitRestore().toEntitlement()
            if (BillingIdentity.sessionToken != session) throw AppError.SessionExpired()
            _entitlement.value = entitlement
            entitlement
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
        groupCapacity = groupCapacityForProduct(premium.productIdentifier),
        productId = premium.productIdentifier,
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
    val freePhase = product.defaultOption?.freePhase
    return mapRevenueCatPlan(
        packageIdentifier = identifier,
        storeProductId = product.id,
        packageType = packageType,
        formattedPrice = product.price.formatted,
        trialUnitName = freePhase?.billingPeriod?.unit?.name,
        trialValue = freePhase?.billingPeriod?.value,
    )
}

/** Pure mapping kept testable without constructing RevenueCat SDK objects. */
internal fun mapRevenueCatPlan(
    packageIdentifier: String,
    storeProductId: String,
    packageType: PackageType,
    formattedPrice: String,
    trialUnitName: String?,
    trialValue: Int?,
): SubscriptionPlan? {
    val period = when (packageType) {
        PackageType.MONTHLY -> BillingPeriod.Monthly
        PackageType.ANNUAL -> BillingPeriod.Annual
        PackageType.CUSTOM -> when {
            packageIdentifier.endsWith("_monthly", ignoreCase = true) -> BillingPeriod.Monthly
            packageIdentifier.endsWith("_annual", ignoreCase = true) -> BillingPeriod.Annual
            else -> return null
        }
        else -> return null
    }

    val trialUnit = when (trialUnitName?.uppercase()) {
        "DAY" -> TrialUnit.Day
        "WEEK" -> TrialUnit.Week
        "MONTH" -> TrialUnit.Month
        "YEAR" -> TrialUnit.Year
        else -> null
    }
    val trial = if (trialUnit != null && trialValue != null && trialValue > 0) {
        TrialPeriod(value = trialValue, unit = trialUnit)
    } else {
        null
    }

    return SubscriptionPlan(
        // Monthly and annual base plans commonly share one Play product ID.
        // RevenueCat's package identifier is the unique purchase target.
        id = packageIdentifier,
        storeProductId = storeProductId,
        period = period,
        // Already localised by Play, including currency and separators.
        price = formattedPrice,
        groupCapacity = groupCapacityForProduct(storeProductId),
        trial = trial,
    )
}

/**
 * Product IDs are a durable store contract. The original product covers one
 * group; higher tiers use `pamoja_circle_<capacity>_v<version>`.
 */
internal fun groupCapacityForProduct(productIdentifier: String): Int {
    val productId = productIdentifier.substringBefore(':')
    val parsed = Regex("^pamoja_circle_(\\d+)_v\\d+$")
        .matchEntire(productId)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    return parsed?.coerceIn(1, 50) ?: 1
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
    if (this is CancellationException) throw this
    if (this is AppError) return this

    val error = (this as? PurchasesException)?.error
        ?: (this as? PurchasesError)
        ?: return AppError.Unknown(message ?: "Something went wrong with the purchase")

    return mapRevenueCatBillingError(
        errorCodeName = error.code.name,
        userCancelled = (this as? PurchasesTransactionException)?.userCancelled == true,
        detail = error.message,
    )
}

/** Pure mapping so cancellation and pending payment cannot regress behind SDK objects. */
internal fun mapRevenueCatBillingError(
    errorCodeName: String,
    userCancelled: Boolean,
    detail: String,
): AppError {
    if (userCancelled || errorCodeName == PurchasesErrorCode.PurchaseCancelledError.name) {
        return AppError.Billing(BillingFailure.UserCancelled, detail)
    }

    return when (errorCodeName) {
        PurchasesErrorCode.NetworkError.name -> AppError.Offline()
        PurchasesErrorCode.PaymentPendingError.name ->
            AppError.Billing(BillingFailure.PaymentPending, detail)
        PurchasesErrorCode.PurchaseNotAllowedError.name ->
            AppError.Billing(BillingFailure.PurchaseNotAllowed, detail)
        PurchasesErrorCode.ProductNotAvailableForPurchaseError.name ->
            AppError.Billing(BillingFailure.ProductUnavailable, detail)
        PurchasesErrorCode.ProductAlreadyPurchasedError.name ->
            AppError.Billing(BillingFailure.AlreadyOwned, detail)
        else -> AppError.Unknown(detail)
    }
}
