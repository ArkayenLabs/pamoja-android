package com.pamoja.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundRestrictionsTest {

    @Test
    fun brandsMapToTheSkinWhoseSwitchesTheyShare() {
        listOf("OPPO", "realme", "OnePlus").forEach {
            assertEquals(it, PhoneMaker.ColorOs, BackgroundRestrictions.makerOf(it))
        }
        listOf("Xiaomi", "Redmi", "POCO").forEach {
            assertEquals(it, PhoneMaker.Miui, BackgroundRestrictions.makerOf(it))
        }
        assertEquals(PhoneMaker.Vivo, BackgroundRestrictions.makerOf("iQOO"))
        assertEquals(PhoneMaker.Samsung, BackgroundRestrictions.makerOf(" samsung "))
        assertEquals(PhoneMaker.Huawei, BackgroundRestrictions.makerOf("HONOR"))
    }

    @Test
    fun phonesThatBehaveLikeStockAndroidAreNotAsked() {
        listOf("Google", "motorola", "Nothing", "", null).forEach {
            assertNull(BackgroundRestrictions.makerOf(it))
        }
    }
}
