package com.pamoja.app.util

import androidx.core.content.UnusedAppRestrictionsConstants.API_30
import androidx.core.content.UnusedAppRestrictionsConstants.API_30_BACKPORT
import androidx.core.content.UnusedAppRestrictionsConstants.API_31
import androidx.core.content.UnusedAppRestrictionsConstants.DISABLED
import androidx.core.content.UnusedAppRestrictionsConstants.ERROR
import androidx.core.content.UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppHibernationTest {

    private fun ask(
        status: Int? = API_31,
        asked: Boolean? = false,
        inAGroup: Boolean = true,
        tourSeen: Boolean? = true,
    ) = AppHibernation.shouldAsk(status, asked, inAGroup, tourSeen)

    @Test
    fun asksAGroupMemberWhenHibernationIsOn() = assertTrue(ask())

    @Test
    fun neverAsksTwice() = assertFalse(ask(asked = true))

    @Test
    fun waitsUntilTheAnswerHasBeenRead() = assertFalse(ask(asked = null))

    @Test
    fun doesNotAskSomeoneWithNoGroup() = assertFalse(ask(inAGroup = false))

    @Test
    fun waitsForHomesTour() {
        assertFalse(ask(tourSeen = false))
        assertFalse(ask(tourSeen = null))
    }

    @Test
    fun doesNotAskWhenAlreadyOffOrNotApplicable() {
        for (status in listOf(DISABLED, FEATURE_NOT_AVAILABLE, ERROR, null)) {
            assertFalse("status $status", ask(status = status))
        }
    }

    @Test
    fun doesNotAskWherePermissionResetIsAllThatHappens() {
        assertFalse(ask(status = API_30))
        assertFalse(ask(status = API_30_BACKPORT))
    }
}
