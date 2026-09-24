package com.pamoja.app.data.repository

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.BillingFailure
import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.repository.TrialUnit
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchasesErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevenueCatPlanMapperTest {

    @Test
    fun `monthly plan keeps package id and localized store price`() {
        val plan = mapRevenueCatPlan(
            packageIdentifier = "circle_monthly",
            storeProductId = "pamoja_circle",
            packageType = PackageType.MONTHLY,
            formattedPrice = "₹199.00",
            trialUnitName = null,
            trialValue = null,
        )

        requireNotNull(plan)
        assertEquals("circle_monthly", plan.id)
        assertEquals("pamoja_circle", plan.storeProductId)
        assertEquals(BillingPeriod.Monthly, plan.period)
        assertEquals("₹199.00", plan.price)
        assertNull(plan.trial)
    }

    @Test
    fun `month trial stays one month instead of becoming thirty days`() {
        val plan = mapRevenueCatPlan(
            packageIdentifier = "circle_annual",
            storeProductId = "pamoja_circle",
            packageType = PackageType.ANNUAL,
            formattedPrice = "\$29.99",
            trialUnitName = "MONTH",
            trialValue = 1,
        )

        requireNotNull(plan)
        assertEquals(1, plan.trial?.value)
        assertEquals(TrialUnit.Month, plan.trial?.unit)
    }

    @Test
    fun `unsupported weekly package is not exposed`() {
        val plan = mapRevenueCatPlan(
            packageIdentifier = "circle_weekly",
            storeProductId = "pamoja_circle",
            packageType = PackageType.WEEKLY,
            formattedPrice = "\$1.99",
            trialUnitName = null,
            trialValue = null,
        )

        assertNull(plan)
    }

    @Test
    fun `custom multi-group package exposes capacity and billing period`() {
        val plan = mapRevenueCatPlan(
            packageIdentifier = "circle_2_monthly",
            storeProductId = "pamoja_circle_2_v1:monthly-autorenewing",
            packageType = PackageType.CUSTOM,
            formattedPrice = "\$6.99",
            trialUnitName = null,
            trialValue = null,
        )

        requireNotNull(plan)
        assertEquals(BillingPeriod.Monthly, plan.period)
        assertEquals(2, plan.groupCapacity)
    }

    @Test
    fun `user cancellation is a typed non failure experience`() {
        val error = mapRevenueCatBillingError(
            errorCodeName = PurchasesErrorCode.PurchaseCancelledError.name,
            userCancelled = true,
            detail = "cancelled",
        )

        assertTrue(error is AppError.Billing)
        assertEquals(BillingFailure.UserCancelled, (error as AppError.Billing).reason)
    }

    @Test
    fun `pending payment is not confused with permission denial`() {
        val error = mapRevenueCatBillingError(
            errorCodeName = PurchasesErrorCode.PaymentPendingError.name,
            userCancelled = false,
            detail = "pending",
        )

        assertTrue(error is AppError.Billing)
        assertEquals(BillingFailure.PaymentPending, (error as AppError.Billing).reason)
    }
}
