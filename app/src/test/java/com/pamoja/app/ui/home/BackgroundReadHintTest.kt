package com.pamoja.app.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundReadHintTest {

    private fun needed(
        inAGroup: Boolean = true,
        stepsGranted: Boolean = true,
        supported: Boolean = true,
        granted: Boolean = false,
        dismissed: Boolean? = false,
    ) = backgroundHintNeeded(inAGroup, stepsGranted, supported, granted, dismissed)

    @Test
    fun showsWhenStepsArriveOnlyWhileOpen() = assertTrue(needed())

    @Test
    fun hiddenOnceBackgroundIsGranted() = assertFalse(needed(granted = true))

    /** Without steps the "connect" card is the one to show, not this. */
    @Test
    fun hiddenWithoutStepAccess() = assertFalse(needed(stepsGranted = false))

    @Test
    fun hiddenWhereThePhoneCannotGrantIt() = assertFalse(needed(supported = false))

    @Test
    fun hiddenForSomeoneWithNoGroup() = assertFalse(needed(inAGroup = false))

    @Test
    fun hiddenAfterNotNowAndUntilThatIsKnown() {
        assertFalse(needed(dismissed = true))
        assertFalse(needed(dismissed = null))
    }
}
