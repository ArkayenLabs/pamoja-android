package com.pamoja.app.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.model.Entitlement
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.domain.usecase.GetSubscriptionPlansUseCase
import com.pamoja.app.domain.usecase.ObserveEntitlementUseCase
import com.pamoja.app.domain.usecase.PurchaseSubscriptionUseCase
import com.pamoja.app.domain.usecase.RestorePurchasesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaywallUiState(
    val isLoading: Boolean = true,
    val entitlement: Entitlement = Entitlement(),
    val plans: List<SubscriptionPlan> = emptyList(),
    /** Which plan the user has selected. Annual by default, see below. */
    val selectedPlanId: String? = null,

    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val error: AppError? = null,
    /** Set the moment Premium is active, which is what closes the screen. */
    val justUpgraded: Boolean = false,
) {
    /**
     * True when there is genuinely nothing to sell.
     *
     * Reached today because no billing provider is configured, and reached in
     * production whenever the store is unreachable or the products are
     * misconfigured. Same state, same handling, so the case cannot rot.
     */
    val plansUnavailable: Boolean get() = !isLoading && plans.isEmpty()

    val selectedPlan: SubscriptionPlan?
        get() = plans.firstOrNull { it.id == selectedPlanId }
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
    private val observeEntitlementUseCase: ObserveEntitlementUseCase,
    private val getSubscriptionPlansUseCase: GetSubscriptionPlansUseCase,
    private val purchaseSubscriptionUseCase: PurchaseSubscriptionUseCase,
    private val restorePurchasesUseCase: RestorePurchasesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        observeEntitlement()
        loadPlans()
    }

    private fun observeEntitlement() {
        viewModelScope.launch {
            observeEntitlementUseCase().collect { entitlement ->
                _uiState.value = _uiState.value.copy(entitlement = entitlement)
            }
        }
    }

    fun loadPlans() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            getSubscriptionPlansUseCase().fold(
                onSuccess = { plans ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        plans = plans,
                        // Annual preselected: it is where the revenue is in this
                        // category, and it carries the trial. Preselecting
                        // monthly would bury both.
                        selectedPlanId = _uiState.value.selectedPlanId
                            ?: plans.firstOrNull { it.period == BillingPeriod.Annual }?.id
                            ?: plans.firstOrNull()?.id,
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

    fun purchase(activity: Activity) {
        val planId = _uiState.value.selectedPlanId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPurchasing = true, error = null)

            purchaseSubscriptionUseCase(planId, activity).fold(
                onSuccess = { entitlement ->
                    _uiState.value = _uiState.value.copy(
                        isPurchasing = false,
                        entitlement = entitlement,
                        justUpgraded = entitlement.isPremium,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isPurchasing = false,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    /**
     * For a reinstall or a new phone, where the purchase exists at the store
     * but this install has never seen it.
     */
    fun restore() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true, error = null)

            restorePurchasesUseCase().fold(
                onSuccess = { entitlement ->
                    _uiState.value = _uiState.value.copy(
                        isRestoring = false,
                        entitlement = entitlement,
                        justUpgraded = entitlement.isPremium,
                        // Nothing to restore is not a failure, but saying
                        // nothing at all reads as a dead button.
                        error = if (entitlement.isPremium) null else AppError.NotFound(),
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isRestoring = false,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
