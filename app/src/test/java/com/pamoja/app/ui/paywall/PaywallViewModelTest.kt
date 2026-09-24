package com.pamoja.app.ui.paywall

import androidx.lifecycle.SavedStateHandle
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.BillingFailure
import com.pamoja.app.domain.error.SponsorshipFailure
import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.model.Entitlement
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.PlanTier
import com.pamoja.app.domain.repository.GroupSponsorship
import com.pamoja.app.domain.repository.GroupSponsorshipRepository
import com.pamoja.app.domain.repository.PurchaseContext
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.domain.repository.SubscriptionRepository
import com.pamoja.app.domain.usecase.ActivateGroupSponsorshipUseCase
import com.pamoja.app.domain.usecase.ConfirmPurchasedGroupAccessUseCase
import com.pamoja.app.domain.usecase.GetSubscriptionPlansUseCase
import com.pamoja.app.domain.usecase.ObserveEntitlementUseCase
import com.pamoja.app.domain.usecase.PurchaseSubscriptionUseCase
import com.pamoja.app.domain.usecase.ResolveSponsorshipGroupUseCase
import com.pamoja.app.domain.usecase.RestorePurchasesUseCase
import com.pamoja.app.testing.SponsorshipAuthRepository
import com.pamoja.app.testing.SponsorshipGroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    @Test
    fun `active group never opens checkout`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val billing = SequencedSponsorshipRepository(mutableListOf(successfulSponsorship()))
            billing.context = defaultContext().copy(groupIsPremium = true)
            val store = FakeSubscriptionRepository(listOf(monthlyPlan))
            val vm = store.createViewModel(sponsorshipRepository = billing)
            advanceUntilIdle()
            vm.purchase(Any())
            advanceUntilIdle()
            assertEquals(0, store.availablePlansCalls)
            assertEquals(0, store.purchaseCalls)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `existing subscription moves only after consent and without checkout`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val billing = SequencedSponsorshipRepository(mutableListOf(successfulSponsorship()))
            billing.context = defaultContext().copy(subscriptionIsActive = true,
                sponsoredGroupId = "another-group", sponsoredGroupName = "Previous group",
                sponsoredGroupIds = listOf("another-group"))
            val store = FakeSubscriptionRepository(listOf(monthlyPlan))
            val vm = store.createViewModel(sponsorshipRepository = billing)
            advanceUntilIdle()
            vm.purchase(Any())
            advanceUntilIdle()
            assertTrue(billing.groupIds.isEmpty())
            vm.purchase(Any(), confirmMove = true)
            advanceUntilIdle()
            assertEquals(0, store.purchaseCalls)
            assertEquals(listOf(true), billing.replaceExistingValues)
            assertTrue(vm.uiState.value.justUpgraded)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `keep both upgrades capacity and preserves the existing group`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val billing = SequencedSponsorshipRepository(mutableListOf(successfulSponsorship()))
            billing.context = defaultContext().copy(
                subscriptionIsActive = true,
                sponsoredGroupId = "another-group",
                sponsoredGroupName = "Previous group",
                groupCapacity = 1,
                sponsoredGroupIds = listOf("another-group"),
            )
            val current = premiumEntitlement.copy(
                groupCapacity = 1,
                productId = "pamoja_circle_v1:monthly-autorenewing",
            )
            val upgraded = premiumEntitlement.copy(
                groupCapacity = 2,
                productId = "pamoja_circle_2_v1:monthly-autorenewing",
            )
            val store = FakeSubscriptionRepository(
                plans = listOf(twoGroupMonthlyPlan),
                purchaseResult = Result.success(upgraded),
                initialEntitlement = current,
            )
            val vm = store.createViewModel(sponsorshipRepository = billing)
            advanceUntilIdle()

            vm.keepBothGroupsPremium(Any())
            advanceUntilIdle()

            assertEquals(1, store.purchaseCalls)
            assertEquals(current.productId, store.lastReplacingProductId)
            assertEquals(listOf(false), billing.replaceExistingValues)
            assertTrue(vm.uiState.value.justUpgraded)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `account switch before checkout fails closed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val billing = SequencedSponsorshipRepository(mutableListOf(successfulSponsorship()))
            val store = FakeSubscriptionRepository(listOf(monthlyPlan))
            val vm = store.createViewModel(sponsorshipRepository = billing)
            advanceUntilIdle()
            billing.context = billing.context.copy(payerUid = "different-user")
            vm.purchase(Any())
            advanceUntilIdle()
            assertEquals(0, store.purchaseCalls)
            assertTrue(vm.uiState.value.groupError is AppError.SessionExpired)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `double tap opens only one checkout`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeSubscriptionRepository(listOf(monthlyPlan), Result.success(premiumEntitlement))
            val vm = store.createViewModel()
            advanceUntilIdle()
            vm.purchase(Any())
            vm.purchase(Any())
            advanceUntilIdle()
            assertEquals(1, store.purchaseCalls)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `monthly plan is the transparent lower commitment default`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeSubscriptionRepository(
                plans = listOf(annualPlan, monthlyPlan),
            )

            val viewModel = repository.createViewModel()
            advanceUntilIdle()

            assertEquals(listOf(monthlyPlan, annualPlan), viewModel.uiState.value.plans)
            assertEquals(monthlyPlan.id, viewModel.uiState.value.selectedPlanId)
            assertEquals("₹199.00", viewModel.uiState.value.selectedPlan?.price)
            assertEquals(GROUP_NAME, viewModel.uiState.value.group?.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `removed package cannot remain selected after plans reload`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeSubscriptionRepository(
                plans = listOf(monthlyPlan, annualPlan),
            )
            val viewModel = repository.createViewModel()
            advanceUntilIdle()

            viewModel.selectPlan(annualPlan.id)
            repository.plans = listOf(monthlyPlan)
            viewModel.loadPlans()
            advanceUntilIdle()

            assertEquals(monthlyPlan.id, viewModel.uiState.value.selectedPlanId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `invalid route never loads products`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeSubscriptionRepository(plans = listOf(monthlyPlan))

            val viewModel = repository.createViewModel(groupId = "not-a-group")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.group)
            assertFalse(viewModel.uiState.value.isGroupLoading)
            assertEquals(0, repository.availablePlansCalls)
            assertSponsorshipFailure(
                viewModel.uiState.value.groupError,
                SponsorshipFailure.InvalidGroup,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `former member never sees products for the stale group`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeSubscriptionRepository(plans = listOf(monthlyPlan))
            val groupRepository = SponsorshipGroupRepository(
                groupResult = Result.success(Group(groupId = GROUP_ID, name = GROUP_NAME)),
                membershipResult = Result.failure(AppError.NotFound()),
            )

            val viewModel = repository.createViewModel(groupRepository = groupRepository)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.group)
            assertEquals(0, repository.availablePlansCalls)
            assertSponsorshipFailure(
                viewModel.uiState.value.groupError,
                SponsorshipFailure.NotCurrentMember,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `store entitlement never closes paywall before named group is server confirmed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val subscriptions = FakeSubscriptionRepository(
                plans = listOf(monthlyPlan),
                purchaseResult = Result.success(premiumEntitlement),
            )
            val sponsorships = BlockingSponsorshipRepository()
            val viewModel = subscriptions.createViewModel(sponsorshipRepository = sponsorships)
            advanceUntilIdle()

            viewModel.purchase(Any())
            runCurrent()

            assertTrue(viewModel.uiState.value.hasUnconfirmedPurchase)
            assertTrue(viewModel.uiState.value.isVerifyingSponsorship)
            assertFalse(viewModel.uiState.value.justUpgraded)
            assertEquals(GROUP_ID, sponsorships.receivedGroupId)

            sponsorships.result.complete(successfulSponsorship())
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasUnconfirmedPurchase)
            assertTrue(viewModel.uiState.value.justUpgraded)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `short RevenueCat delay retries the same group and then confirms access`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val waiting = Result.failure<GroupSponsorship>(
                AppError.Sponsorship(SponsorshipFailure.NoActiveSubscription)
            )
            val sponsorships = SequencedSponsorshipRepository(
                mutableListOf(waiting, waiting, successfulSponsorship())
            )
            val subscriptions = FakeSubscriptionRepository(
                plans = listOf(monthlyPlan),
                purchaseResult = Result.success(premiumEntitlement),
            )
            val viewModel = subscriptions.createViewModel(sponsorshipRepository = sponsorships)
            advanceUntilIdle()

            viewModel.purchase(Any())
            advanceUntilIdle()

            assertEquals(listOf(GROUP_ID, GROUP_ID, GROUP_ID), sponsorships.groupIds)
            assertEquals(1, subscriptions.purchaseCalls)
            assertTrue(viewModel.uiState.value.justUpgraded)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed verification can retry without opening checkout twice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val assignedElsewhere = Result.failure<GroupSponsorship>(
                AppError.Network("Temporary verification failure")
            )
            val sponsorships = SequencedSponsorshipRepository(
                mutableListOf(assignedElsewhere, successfulSponsorship())
            )
            val subscriptions = FakeSubscriptionRepository(
                plans = listOf(monthlyPlan),
                purchaseResult = Result.success(premiumEntitlement),
            )
            val viewModel = subscriptions.createViewModel(sponsorshipRepository = sponsorships)
            advanceUntilIdle()

            viewModel.purchase(Any())
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasUnconfirmedPurchase)
            assertFalse(viewModel.uiState.value.justUpgraded)
            assertTrue(viewModel.uiState.value.sponsorshipError is AppError.Network)

            viewModel.retrySponsorshipVerification()
            advanceUntilIdle()

            assertEquals(1, subscriptions.purchaseCalls)
            assertEquals(2, sponsorships.groupIds.size)
            assertTrue(viewModel.uiState.value.justUpgraded)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `moving Premium requires a separate explicit action after assignment conflict`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val assignedElsewhere = Result.failure<GroupSponsorship>(
                AppError.Sponsorship(SponsorshipFailure.SubscriptionAssignedElsewhere)
            )
            val sponsorships = SequencedSponsorshipRepository(
                mutableListOf(assignedElsewhere, successfulSponsorship())
            )
            val subscriptions = FakeSubscriptionRepository(
                plans = listOf(monthlyPlan),
                restoreResult = Result.success(premiumEntitlement),
            )
            val viewModel = subscriptions.createViewModel(sponsorshipRepository = sponsorships)
            advanceUntilIdle()

            viewModel.restore()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasUnconfirmedPurchase)
            assertSponsorshipFailure(
                viewModel.uiState.value.sponsorshipError,
                SponsorshipFailure.SubscriptionAssignedElsewhere,
            )
            assertEquals(listOf(false), sponsorships.replaceExistingValues)

            viewModel.moveSponsorshipHere()
            advanceUntilIdle()

            assertEquals(listOf(false, true), sponsorships.replaceExistingValues)
            assertTrue(viewModel.uiState.value.justUpgraded)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cancelling Google Play is silent and never reaches sponsorship`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val subscriptions = FakeSubscriptionRepository(
                plans = listOf(monthlyPlan),
                purchaseResult = Result.failure(
                    AppError.Billing(BillingFailure.UserCancelled)
                ),
            )
            val sponsorships = SequencedSponsorshipRepository(
                mutableListOf(successfulSponsorship())
            )
            val viewModel = subscriptions.createViewModel(sponsorshipRepository = sponsorships)
            advanceUntilIdle()

            viewModel.purchase(Any())
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.hasUnconfirmedPurchase)
            assertFalse(viewModel.uiState.value.justUpgraded)
            assertTrue(sponsorships.groupIds.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `restore assigns an active purchase to the exact visible group`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val subscriptions = FakeSubscriptionRepository(
                plans = listOf(monthlyPlan),
                restoreResult = Result.success(premiumEntitlement),
            )
            val sponsorships = SequencedSponsorshipRepository(
                mutableListOf(successfulSponsorship())
            )
            val viewModel = subscriptions.createViewModel(sponsorshipRepository = sponsorships)
            advanceUntilIdle()

            viewModel.restore()
            advanceUntilIdle()

            assertEquals(listOf(GROUP_ID), sponsorships.groupIds)
            assertTrue(viewModel.uiState.value.justUpgraded)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun assertSponsorshipFailure(error: AppError?, expected: SponsorshipFailure) {
        assertTrue(error is AppError.Sponsorship)
        assertEquals(expected, (error as AppError.Sponsorship).reason)
    }

    private fun FakeSubscriptionRepository.createViewModel(
        groupId: String = GROUP_ID,
        groupRepository: SponsorshipGroupRepository = SponsorshipGroupRepository(
            groupResult = Result.success(Group(groupId = GROUP_ID, name = GROUP_NAME)),
            membershipResult = Result.success(
                Membership(userId = MEMBER_ID, groupId = GROUP_ID),
            ),
        ),
        sponsorshipRepository: GroupSponsorshipRepository =
            SequencedSponsorshipRepository(mutableListOf(successfulSponsorship())),
    ) = PaywallViewModel(
        savedStateHandle = SavedStateHandle(mapOf("groupId" to groupId)),
        resolveSponsorshipGroupUseCase = ResolveSponsorshipGroupUseCase(
            authRepository = SponsorshipAuthRepository(),
            groupRepository = groupRepository,
        ),
        observeEntitlementUseCase = ObserveEntitlementUseCase(this),
        getSubscriptionPlansUseCase = GetSubscriptionPlansUseCase(this),
        purchaseSubscriptionUseCase = PurchaseSubscriptionUseCase(this),
        restorePurchasesUseCase = RestorePurchasesUseCase(this),
        confirmPurchasedGroupAccessUseCase = ConfirmPurchasedGroupAccessUseCase(
            ActivateGroupSponsorshipUseCase(sponsorshipRepository)
        ),
    )

    private class FakeSubscriptionRepository(
        var plans: List<SubscriptionPlan>,
        var purchaseResult: Result<Entitlement> = Result.success(Entitlement()),
        var restoreResult: Result<Entitlement> = Result.success(Entitlement()),
        initialEntitlement: Entitlement = Entitlement(),
    ) : SubscriptionRepository {
        var availablePlansCalls: Int = 0
        var purchaseCalls: Int = 0
        var lastReplacingProductId: String? = null
        override val entitlement: Flow<Entitlement> = MutableStateFlow(initialEntitlement)

        override suspend fun availablePlans(): Result<List<SubscriptionPlan>> {
            availablePlansCalls += 1
            return Result.success(plans)
        }

        override suspend fun purchase(
            planId: String,
            activity: Any,
            replacingProductId: String?,
        ): Result<Entitlement> {
            purchaseCalls += 1
            lastReplacingProductId = replacingProductId
            return purchaseResult
        }

        override suspend fun restorePurchases(): Result<Entitlement> =
            restoreResult
    }

    private class SequencedSponsorshipRepository(
        private val results: MutableList<Result<GroupSponsorship>>,
    ) : GroupSponsorshipRepository {
        var context = defaultContext()
        override suspend fun purchaseContext(groupId: String) = Result.success(context)
        override suspend fun activateChecked(context: PurchaseContext, replaceExisting: Boolean) =
            activate(context.groupId, replaceExisting)
        val groupIds = mutableListOf<String>()

        val replaceExistingValues = mutableListOf<Boolean>()

        override suspend fun activate(
            groupId: String,
            replaceExisting: Boolean,
        ): Result<GroupSponsorship> {
            groupIds += groupId
            replaceExistingValues += replaceExisting
            return if (results.size > 1) results.removeAt(0) else results.first()
        }
    }

    private class BlockingSponsorshipRepository : GroupSponsorshipRepository {
        override suspend fun purchaseContext(groupId: String) = Result.success(defaultContext())
        override suspend fun activateChecked(context: PurchaseContext, replaceExisting: Boolean) =
            activate(context.groupId, replaceExisting)
        val result = CompletableDeferred<Result<GroupSponsorship>>()
        var receivedGroupId: String? = null

        override suspend fun activate(
            groupId: String,
            replaceExisting: Boolean,
        ): Result<GroupSponsorship> {
            receivedGroupId = groupId
            return result.await()
        }
    }

    private companion object {
        fun defaultContext() = PurchaseContext(GROUP_ID, MEMBER_ID, false, false, false, null, null)
        const val GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817"
        const val GROUP_NAME = "Asha's Sunrise Walkers"
        const val MEMBER_ID = "member-1"
        val premiumEntitlement = Entitlement(tier = PlanTier.Premium)
        fun successfulSponsorship(): Result<GroupSponsorship> = Result.success(
            GroupSponsorship(
                groupId = GROUP_ID,
                leaseValidUntilMillis = 1_800_000_000_000L,
            )
        )
        val monthlyPlan = SubscriptionPlan(
            id = "circle_monthly",
            storeProductId = "pamoja_circle",
            period = BillingPeriod.Monthly,
            price = "₹199.00",
        )
        val annualPlan = SubscriptionPlan(
            id = "circle_annual",
            storeProductId = "pamoja_circle",
            period = BillingPeriod.Annual,
            price = "₹1,499.00",
        )
        val twoGroupMonthlyPlan = SubscriptionPlan(
            id = "circle_2_monthly",
            storeProductId = "pamoja_circle_2_v1:monthly-autorenewing",
            period = BillingPeriod.Monthly,
            price = "₹349.00",
            groupCapacity = 2,
        )
    }
}
