package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.BillingPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnualSavingTest {
    private fun plan(period: BillingPeriod, micros: Long, currency: String = "INR") = SubscriptionPlan(
        id = period.name, storeProductId = "pamoja_premium", period = period, price = "",
        priceMicros = micros, currencyCode = currency,
    )

    @Test
    fun theTestersPricesSaveThirtySevenPercentRoundedDown() {
        // Rs 200 a month and Rs 1,500 a year: 2,400 against 1,500 is 37.5%.
        assertEquals(37, annualSavingPercent(plan(BillingPeriod.Monthly, 200_000_000), plan(BillingPeriod.Annual, 1_500_000_000)))
    }

    @Test
    fun nothingIsClaimedWhenTheAmountsCannotBeTrusted() {
        val monthly = plan(BillingPeriod.Monthly, 200_000_000)
        assertNull(annualSavingPercent(monthly, plan(BillingPeriod.Annual, 0)))
        assertNull(annualSavingPercent(monthly, plan(BillingPeriod.Annual, 1_500_000_000, currency = "USD")))
        assertNull(annualSavingPercent(plan(BillingPeriod.Monthly, 200_000_000, currency = ""), plan(BillingPeriod.Annual, 1_500_000_000, currency = "")))
    }

    @Test
    fun aTinyOrNegativeSavingIsNotAdvertised() {
        val monthly = plan(BillingPeriod.Monthly, 100_000_000)
        assertNull(annualSavingPercent(monthly, plan(BillingPeriod.Annual, 1_170_000_000)))
        assertNull(annualSavingPercent(monthly, plan(BillingPeriod.Annual, 1_300_000_000)))
        assertEquals(5, annualSavingPercent(monthly, plan(BillingPeriod.Annual, 1_140_000_000)))
    }
}
