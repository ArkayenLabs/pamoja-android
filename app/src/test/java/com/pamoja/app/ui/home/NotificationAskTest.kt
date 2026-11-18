package com.pamoja.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationAskTest {

    private fun next(
        firstShown: Boolean = true,
        secondShown: Boolean = false,
        cheerReceived: Boolean = true,
        askedThisVisit: Boolean = false,
    ) = nextNotificationAsk(firstShown, secondShown, cheerReceived, askedThisVisit)

    @Test
    fun firstAskAtAnySignal() {
        assertEquals(NotificationAsk.First, next(firstShown = false, cheerReceived = false))
        assertEquals(NotificationAsk.First, next(firstShown = false, cheerReceived = true))
    }

    @Test
    fun secondAskOnlyAtAReceivedHighFive() {
        assertEquals(NotificationAsk.Second, next())
        assertNull(next(cheerReceived = false))
    }

    @Test
    fun secondAskNeverInTheSameVisitAsTheFirst() = assertNull(next(askedThisVisit = true))

    @Test
    fun neverAThirdTime() = assertNull(next(secondShown = true))
}
