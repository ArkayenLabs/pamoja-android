package com.pamoja.app.ui.paywall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.BillingFailure
import com.pamoja.app.domain.error.SponsorshipFailure
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.model.Entitlement
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.domain.repository.PurchaseContext
import com.pamoja.app.domain.usecase.GetSubscriptionPlansUseCase
import com.pamoja.app.domain.usecase.ConfirmPurchasedGroupAccessUseCase
import com.pamoja.app.domain.usecase.ObserveEntitlementUseCase
import com.pamoja.app.domain.usecase.PurchaseSubscriptionUseCase
import com.pamoja.app.domain.usecase.ResolveSponsorshipGroupUseCase
import com.pamoja.app.domain.usecase.RestorePurchasesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaywallUiState(
    /** The server-backed group this purchase would support. */
    val group: Group? = null,
    val purchaseContext: PurchaseContext? = null,
    val isGroupLoading: Boolean = true,
    val groupError: AppError? = null,
    val isLoading: Boolean = false,
    val entitlement: Entitlement = Entitlement(),
    val plans: List<SubscriptionPlan> = emptyList(),
    /** Which RevenueCat package the user selected. */
    val selectedPlanId: String? = null,

    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    /** Store success exists, but the named group is not server-confirmed yet. */
    val hasUnconfirmedPurchase: Boolean = false,
    val isVerifyingSponsorship: Boolean = false,
    val sponsorshipError: AppError? = null,
    val error: AppError? = null,
    /** Set only after the server confirms access for the exact named group. */
    val justUpgraded: Boolean = false,
    /** A completed store upgrade is waiting for its larger capacity server-side. */
    val capacityUpgradePending: Boolean = false,
) {
    /**
     * True when there is genuinely nothing to sell.
     *
     * Reached today because no billing provider is configured, and reached in
     * production whenever the store is unreachable or the products are
     * misconfigured. Same state, same handling, so the case cannot rot.
     */
    val plansUnavailable: Boolean
        get() = group != null && !isLoading && plans.isEmpty()

    val selectedPlan: SubscriptionPlan?
        get() = plans.firstOrNull { it.id == selectedPlanId }

    val isBusy: Boolean
        get() = isPurchasing || isRestoring || isVerifyingSponsorship
}

/**
 * The upgrade screen.
 *
 * Deliberately does not know what Premium unlocks. The limits it advertises
 * come from `PlanLimits`, the same object the gates read, so the promise and
 * the enforcement cannot drift apart.
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resolveSponsorshipGroupUseCase: ResolveSponsorshipGroupUseCase,
    private val observeEntitlementUseCase: ObserveEntitlementUseCase,
    private val getSubscriptionPlansUseCase: GetSubscriptionPlansUseCase,
    private val purchaseSubscriptionUseCase: PurchaseSubscriptionUseCase,
    private val restorePurchasesUseCase: RestorePurchasesUseCase,
    private val confirmPurchasedGroupAccessUseCase: ConfirmPurchasedGroupAccessUseCase,
) : ViewModel() {

    private val requestedGroupId: String =
        savedStateHandle.get<String>("groupId").orEmpty()

    private val _uiState = MutableStateFlow(PaywallUiState())
    private var confirmedMove = false
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        observeEntitlement()
        loadGroupContext()
    }

    private fun observeEntitlement() {
        viewModelScope.launch {
            observeEntitlementUseCase().collect { entitlement ->
                _uiState.value = _uiState.value.copy(entitlement = entitlement)
            }
        }
    }

    fun loadGroupContext() {
        if (_uiState.value.isBusy || _uiState.value.hasUnconfirmedPurchase) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                group = null,
                purchaseContext = null,
                isGroupLoading = true,
                groupError = null,
                isLoading = false,
                plans = emptyList(),
                selectedPlanId = null,
            )

            resolveSponsorshipGroupUseCase(requestedGroupId).fold(
                onSuccess = { group ->
                    val contextResult = confirmPurchasedGroupAccessUseCase.purchaseContext(group.groupId)
                    val context = contextResult.getOrNull()
                    if (context == null) {
                        _uiState.value = _uiState.value.copy(isGroupLoading = false,
                            groupError = contextResult.exceptionOrNull()?.toAppError())
                        return@fold
                    }
                    _uiState.value = _uiState.value.copy(
                        group = group,
                        purchaseContext = context,
                        isGroupLoading = false,
                    )
                    if (!context.groupIsPremium &&
                        (!context.subscriptionIsActive ||
                            (context.assignedElsewhere && !context.hasAvailableGroupSlot))) {
                        loadPlans()
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGroupLoading = false,
                        groupError = error.toAppError(),
                    )
                },
            )
        }
    }

    fun loadPlans() {
        if (_uiState.value.group == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            getSubscriptionPlansUseCase().fold(
                onSuccess = { plans ->
                    val context = _uiState.value.purchaseContext
                    val eligiblePlans = if (context?.subscriptionIsActive == true) {
                        val nextCapacity = plans
                            .map { it.groupCapacity }
                            .filter { it > context.groupCapacity }
                            .minOrNull()
                        plans.filter { it.groupCapacity == nextCapacity }
                    } else {
                        plans.filter { it.groupCapacity == 1 }
                    }
                    val orderedPlans = eligiblePlans.sortedBy { plan ->
                        when (plan.period) {
                            BillingPeriod.Monthly -> 0
                            BillingPeriod.Annual -> 1
                        }
                    }
                    val currentSelection = _uiState.value.selectedPlanId
                        ?.takeIf { selectedId ->
                            orderedPlans.any { it.id == selectedId }
                        }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        plans = orderedPlans,
                        // Monthly is the lower-commitment default while Pamoja
                        // is still proving retention. The annual total remains
                        // visible and selectable without steering by default.
                        selectedPlanId = currentSelection
                            ?: orderedPlans.firstOrNull {
                                it.period == BillingPeriod.Monthly
                            }?.id
                            ?: orderedPlans.firstOrNull()?.id,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    fun selectPlan(planId: String) {
        _uiState.value = _uiState.value.copy(selectedPlanId = planId, error = null)
    }

    fun purchase(
        activity: Any,
        confirmMove: Boolean = false,
        upgradeCapacity: Boolean = false,
    ) {
        val group = _uiState.value.group ?: return
        val context = _uiState.value.purchaseContext ?: return
        if (context.groupIsPremium ||
            (context.assignedElsewhere && !confirmMove && !upgradeCapacity)) return
        val planId = _uiState.value.selectedPlanId
        if ((!context.subscriptionIsActive || upgradeCapacity) && planId == null) return
        if (_uiState.value.isBusy || _uiState.value.hasUnconfirmedPurchase) return
        _uiState.value = _uiState.value.copy(isPurchasing = true)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPurchasing = true,
                error = null,
                sponsorshipError = null,
            )

            // Recheck before checkout. A changed account, group or entitlement
            // requires a new user action; never reuse consent for a new context.
            val freshResult = confirmPurchasedGroupAccessUseCase.purchaseContext(group.groupId)
            val fresh = freshResult.getOrNull()
            if (fresh == null) {
                _uiState.value = _uiState.value.copy(isPurchasing = false,
                    error = freshResult.exceptionOrNull()?.toAppError())
                return@launch
            }
            if (fresh.payerUid != context.payerUid) {
                _uiState.value = _uiState.value.copy(isPurchasing = false, group = null,
                    groupError = AppError.SessionExpired())
                return@launch
            }
            if (fresh != context) {
                _uiState.value = _uiState.value.copy(isPurchasing = false, purchaseContext = fresh)
                if (!fresh.groupIsPremium &&
                    (!fresh.subscriptionIsActive ||
                        (fresh.assignedElsewhere && !fresh.hasAvailableGroupSlot))) loadPlans()
                return@launch
            }
            confirmedMove = confirmMove
            if (context.subscriptionIsActive && !upgradeCapacity) {
                _uiState.value = _uiState.value.copy(isPurchasing = false, hasUnconfirmedPurchase = true)
                confirmGroupAccess(group.groupId, confirmMove)
                return@launch
            }

            var replacingProductId: String? = null
            if (upgradeCapacity) {
                replacingProductId = _uiState.value.entitlement.productId
                if (replacingProductId.isNullOrBlank()) {
                    val restored = restorePurchasesUseCase().getOrNull()
                    if (restored != null) {
                        _uiState.value = _uiState.value.copy(entitlement = restored)
                        replacingProductId = restored.productId
                    }
                }
                if (replacingProductId.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isPurchasing = false,
                        error = AppError.Billing(BillingFailure.NothingToRestore),
                    )
                    return@launch
                }
            }
            val purchaseResult = purchaseSubscriptionUseCase(
                requireNotNull(planId), activity, replacingProductId,
            )
            val entitlement = purchaseResult.getOrNull()
            if (entitlement == null) {
                val error = purchaseResult.exceptionOrNull()?.toAppError()
                    ?: AppError.Unknown("Purchase failed without a result")
                _uiState.value = _uiState.value.copy(
                    isPurchasing = false,
                    // Backing out of Google Play is a normal choice, not a red
                    // failure card that pressures the user to continue.
                    error = error.takeUnless { it.isUserCancellation() },
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isPurchasing = false,
                entitlement = entitlement,
                hasUnconfirmedPurchase = true,
                capacityUpgradePending = upgradeCapacity,
            )
            confirmGroupAccess(
                group.groupId,
                replaceExisting = confirmMove,
                retryCapacityIncrease = upgradeCapacity,
            )
        }
    }

    fun keepBothGroupsPremium(activity: Any) {
        purchase(activity = activity, upgradeCapacity = true)
    }

    /**
     * For a reinstall or a new phone, where the purchase exists at the store
     * but this install has never seen it.
     */
    fun restore() {
        val group = _uiState.value.group ?: return
        if (_uiState.value.isBusy || _uiState.value.hasUnconfirmedPurchase) return
        confirmedMove = false
        _uiState.value = _uiState.value.copy(isRestoring = true)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true, error = null)

            val restoreResult = restorePurchasesUseCase()
            val entitlement = restoreResult.getOrNull()
            if (entitlement == null) {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    error = restoreResult.exceptionOrNull()?.toAppError()
                        ?: AppError.Unknown("Restore failed without a result"),
                )
                return@launch
            }
            if (!entitlement.isPremium) {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    entitlement = entitlement,
                    error = AppError.Billing(BillingFailure.NothingToRestore),
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isRestoring = false,
                entitlement = entitlement,
                hasUnconfirmedPurchase = true,
                sponsorshipError = null,
            )
            confirmGroupAccess(group.groupId)
        }
    }

    /** Repeats only the idempotent server check; it never opens checkout again. */
    fun retrySponsorshipVerification() {
        val state = _uiState.value
        val groupId = state.group?.groupId ?: return
        if (!state.hasUnconfirmedPurchase || state.isBusy) return
        _uiState.value = state.copy(isVerifyingSponsorship = true)
        viewModelScope.launch {
            val previous = state.purchaseContext ?: return@launch
            val freshResult = confirmPurchasedGroupAccessUseCase.purchaseContext(groupId)
            val fresh = freshResult.getOrNull()
            if (fresh == null || fresh.payerUid != previous.payerUid) {
                _uiState.value = _uiState.value.copy(isVerifyingSponsorship = false,
                    sponsorshipError = freshResult.exceptionOrNull()?.toAppError() ?: AppError.SessionExpired())
                return@launch
            }
            _uiState.value = _uiState.value.copy(purchaseContext = fresh)
            if (fresh.sponsoredGroupId != previous.sponsoredGroupId && fresh.assignedElsewhere) {
                confirmedMove = false
                _uiState.value = _uiState.value.copy(isVerifyingSponsorship = false,
                    sponsorshipError = AppError.Sponsorship(SponsorshipFailure.SubscriptionAssignedElsewhere))
                return@launch
            }
            confirmGroupAccess(
                groupId,
                replaceExisting = confirmedMove,
                retryCapacityIncrease = state.capacityUpgradePending,
            )
        }
    }

    /** Moves an existing subscription only after the user confirms the change. */
    fun moveSponsorshipHere() {
        val state = _uiState.value
        val groupId = state.group?.groupId ?: return
        val isAssignedElsewhere =
            (state.sponsorshipError as? AppError.Sponsorship)?.reason ==
                SponsorshipFailure.SubscriptionAssignedElsewhere
        if (!state.hasUnconfirmedPurchase || !isAssignedElsewhere || state.isBusy) return
        confirmedMove = true
        _uiState.value = state.copy(isVerifyingSponsorship = true)
        viewModelScope.launch { confirmGroupAccess(groupId, replaceExisting = true) }
    }

    private suspend fun confirmGroupAccess(
        groupId: String,
        replaceExisting: Boolean = false,
        retryCapacityIncrease: Boolean = false,
    ) {
        val context = _uiState.value.purchaseContext ?: return
        _uiState.value = _uiState.value.copy(
            isVerifyingSponsorship = true,
            sponsorshipError = null,
        )
        confirmPurchasedGroupAccessUseCase(
            groupId, replaceExisting, context, retryCapacityIncrease,
        ).fold(
            onSuccess = {
                _uiState.value = _uiState.value.copy(
                    isVerifyingSponsorship = false,
                    hasUnconfirmedPurchase = false,
                    sponsorshipError = null,
                    justUpgraded = true,
                    capacityUpgradePending = false,
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isVerifyingSponsorship = false,
                    sponsorshipError = error.toAppError(),
                )
            },
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

private fun AppError.isUserCancellation(): Boolean =
    this is AppError.Billing && reason == BillingFailure.UserCancelled
