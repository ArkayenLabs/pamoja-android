package com.pamoja.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class SmartNotificationEngineTest {

    @Test
    fun `first sync proves the app knows the real step count and group`() {
        val notification = SmartNotificationEngine.select(
            context(
                userStepsToday = 2_146,
                userStepsThisWeek = 2_146,
                isFirstEverSync = true,
            ),
        )

        assertNotNull(notification)
        assertEquals("2,146 steps. Found them.", notification?.title)
        assertEquals("You're live in bama. The board just changed.", notification?.body)
    }

    @Test
    fun `passing notification names the person and the recoverable gap`() {
        val notification = SmartNotificationEngine.select(
            context(
                memberJustAheadName = "Arjun Mehta",
                stepsToOvertakeMemberAhead = 436,
                memberWhoJustPassedYouName = "Arjun Mehta",
            ),
        )

        assertEquals("Arjun passed you.", notification?.title)
        assertEquals("By 436 steps. That is annoyingly catchable.", notification?.body)
    }

    @Test
    fun `taking first place celebrates the exact lead and rival`() {
        val notification = SmartNotificationEngine.select(
            context(
                userJustTookLead = true,
                memberJustBehindName = "Priya Shah",
                stepsAheadOfMemberBehind = 842,
            ),
        )

        assertEquals("Look who is first.", notification?.title)
        assertEquals(
            "You, by 842 steps. Screenshot it before Priya walks.",
            notification?.body,
        )
    }

    @Test
    fun `final day turns the remaining goal into a per person action`() {
        val notification = SmartNotificationEngine.select(
            context(
                memberCount = 3,
                weeklyGoal = 35_000,
                groupStepsTotal = 32_160,
                userStepsThisWeek = 11_000,
                daysLeftInWeek = 1,
            ),
        )

        assertEquals("Final day. 2,840 left.", notification?.title)
        assertEquals("947 each. Suspiciously doable.", notification?.body)
    }

    @Test
    fun `small recoverable low step day can use the approved gentle roast`() {
        val notification = SmartNotificationEngine.select(
            context(
                userName = "Balaji Rao",
                groupStepsTotal = 1_000,
                userStepsThisWeek = 700,
                userStepsToday = 312,
                daysLeftInWeek = 5,
                now = LocalTime.of(17, 30),
            ),
        )

        assertEquals("Balaji, be honest.", notification?.title)
        assertEquals(
            "312 steps today. Did the phone stay home, or did you?",
            notification?.body,
        )
    }

    @Test
    fun `solo group roast uses the real group name and an action`() {
        val notification = SmartNotificationEngine.select(
            context(
                memberCount = 1,
                groupAgeMs = 13 * 60 * 60 * 1_000L,
            ),
        )

        assertEquals("Strong leader. Tiny team.", notification?.title)
        assertEquals("Still just you in bama. Send the invite.", notification?.body)
    }

    @Test
    fun `all generated samples fit parser limits and contain no em dash`() {
        val contexts = listOf(
            context(goalReachedJustNow = true, groupStepsTotal = 42_252),
            context(isFirstEverSync = true, userStepsToday = 2_146),
            context(
                memberCount = 4,
                newMemberName = "Alexandria-With-A-Very-Long-Name",
            ),
            context(
                memberJustAheadName = "Arjun",
                stepsToOvertakeMemberAhead = 1_200,
                memberWhoJustPassedYouName = "Arjun",
            ),
            context(
                weeklyGoal = 35_000,
                groupStepsTotal = 32_160,
                daysLeftInWeek = 1,
            ),
        )

        val samples = contexts.flatMap(SmartNotificationEngine::debugSamples)
        assertTrue(samples.isNotEmpty())
        samples.forEach { notification ->
            assertTrue(notification.title.length <= 40)
            assertTrue(notification.body.length <= 120)
            assertFalse(notification.title.contains('\u2014'))
            assertFalse(notification.body.contains('\u2014'))
        }
    }

    private fun context(
        userName: String = "Balaji",
        groupName: String = "bama",
        memberCount: Int = 2,
        groupAgeMs: Long = 0,
        weeklyGoal: Long = 35_000,
        groupStepsTotal: Long = 10_000,
        userStepsThisWeek: Long = 5_000,
        userStepsToday: Long = 1_000,
        daysLeftInWeek: Int = 4,
        memberJustAheadName: String? = null,
        stepsToOvertakeMemberAhead: Long = 0,
        memberWhoJustPassedYouName: String? = null,
        userJustTookLead: Boolean = false,
        memberJustBehindName: String? = null,
        stepsAheadOfMemberBehind: Long = 0,
        goalReachedJustNow: Boolean = false,
        newMemberName: String? = null,
        isFirstEverSync: Boolean = false,
        now: LocalTime = LocalTime.of(17, 0),
    ) = NotificationContext(
        userName = userName,
        groupId = "6413795d-42d0-4f2b-80df-f017a9d32817",
        groupName = groupName,
        memberCount = memberCount,
        groupAgeMs = groupAgeMs,
        weeklyGoal = weeklyGoal,
        groupStepsTotal = groupStepsTotal,
        userStepsThisWeek = userStepsThisWeek,
        userStepsToday = userStepsToday,
        daysLeftInWeek = daysLeftInWeek,
        memberJustAheadName = memberJustAheadName,
        stepsToOvertakeMemberAhead = stepsToOvertakeMemberAhead,
        memberWhoJustPassedYouName = memberWhoJustPassedYouName,
        userJustTookLead = userJustTookLead,
        memberJustBehindName = memberJustBehindName,
        stepsAheadOfMemberBehind = stepsAheadOfMemberBehind,
        goalReachedJustNow = goalReachedJustNow,
        newMemberName = newMemberName,
        isFirstEverSync = isFirstEverSync,
        now = now,
        today = DayOfWeek.WEDNESDAY,
        hoursSinceLastNotification = Long.MAX_VALUE,
        consecutiveIgnored = 0,
        rotationSeed = 0,
    )
}
