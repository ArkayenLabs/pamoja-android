package com.pamoja.app.widgets

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WidgetModelTest {
    // Wednesday 30 September 2026, in a group whose week starts on Monday the 28th.
    private val clock = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC)
    private val today = "2026-09-30"
    private val week = "2026-09-28"

    private val group = Group(
        groupId = "g",
        name = "Sharma Family",
        weeklyTarget = 70_000,
        weekStartDay = "MONDAY",
        planningTimeZone = "UTC",
        streakWeeks = 5,
        streakThroughWeek = "2026-09-21",
    )

    private fun member(id: String, todaySteps: Long, week: Long = 10_000, todayDate: String = today, weekStart: String = this.week) =
        Membership(
            userId = id, groupId = "g", displayName = "$id Surname",
            todaySteps = todaySteps, todayDate = todayDate,
            weeklySteps = week, weekStart = weekStart,
        )

    private fun snapshot(vararg members: Membership) = WidgetSnapshot(
        userId = "me",
        group = group,
        members = members.toList(),
        signalsDate = today,
        todaySteps = 6_240,
        stepsDate = today,
        usualSteps = 7_500,
    )

    @Test
    fun nobodyIsListedAsWaitingUntilSomebodyHasWalked() {
        val card = groupCard(snapshot(member("me", 200), member("arjun", 0), member("meera", 900)), clock)!!
        assertEquals(emptyList<String>(), card.waiting)
        assertEquals(emptyList<String>(), card.toNudge)
    }

    @Test
    fun waitingIsEveryoneElseUnderAThousandByFirstName() {
        val card = groupCard(snapshot(member("me", 200), member("priya", 4_800), member("arjun", 300)), clock)!!
        assertEquals(listOf("arjun"), card.waiting)
        assertEquals(listOf("arjun"), card.toNudge)
    }

    @Test
    fun aNudgeSentTodayIsDoneButOneFromYesterdayIsNot() {
        val base = snapshot(member("priya", 4_800), member("arjun", 0), member("meera", 0))
        assertEquals(listOf("meera"), groupCard(base.copy(nudged = setOf("arjun")), clock)!!.toNudge)
        val yesterday = base.copy(nudged = setOf("arjun", "meera"), signalsDate = "2026-09-29")
        assertEquals(listOf("arjun", "meera"), groupCard(yesterday, clock)!!.toNudge)
    }

    @Test
    fun figuresFromAnotherDayOrWeekDoNotCount() {
        val card = groupCard(
            snapshot(
                member("priya", 4_800, week = 20_000),
                member("arjun", 9_000, week = 30_000, todayDate = "2026-09-29", weekStart = "2026-09-21"),
            ),
            clock,
        )!!
        assertEquals(20_000L, card.steps)
        assertEquals(listOf(true, false), card.faces.map { it.walked })
    }

    @Test
    fun streakCountsThisWeekOnceTheGoalIsMet() {
        assertEquals(5, groupCard(snapshot(member("a", 0, week = 10_000)), clock)!!.streak)
        assertEquals(6, groupCard(snapshot(member("a", 0, week = 70_000)), clock)!!.streak)
    }

    @Test
    fun aStreakUnderTwoShowsNoChipAndTheTileShowsTheWeekInstead() {
        val fresh = snapshot(member("a", 0, week = 35_000)).copy(group = group.copy(streakWeeks = 0, streakThroughWeek = ""))
        assertEquals(0, groupCard(fresh, clock)!!.streak)
        assertEquals(StreakModel.Progress(50), streakTile(fresh, clock))
        assertEquals(StreakModel.Streak(5), streakTile(snapshot(member("a", 0)), clock))
        assertEquals(StreakModel.NoGroup, streakTile(snapshot().copy(group = null), clock))
    }

    @Test
    fun aBigGroupShowsFiveFacesAndTheRestAsANumber() {
        val card = groupCard(snapshot(*Array(8) { member("m$it", it * 1_000L) }), clock)!!
        assertEquals(5, card.faces.size)
        assertEquals(3, card.overflow)
        assertEquals("m7", card.faces.first().userId) // most steps today first
    }

    @Test
    fun aCheerTodayTurnsTheTileAndListsWhoToCheerBack() {
        val s = snapshot(member("priya", 0), member("arjun", 0)).copy(
            cheersFrom = listOf("priya", "arjun", "priya"),
            cheeredBack = setOf("priya"),
        )
        val model = youToday(s, clock) as YouTodayModel.Cheered
        assertEquals("priya", model.from.userId)
        assertEquals(1, model.others)
        assertEquals(listOf("arjun"), model.toCheerBack)
    }

    @Test
    fun cheersFromAnotherDayOrFromSomeoneWhoLeftAreIgnored() {
        val old = snapshot(member("priya", 0)).copy(cheersFrom = listOf("priya"), signalsDate = "2026-09-29")
        assertTrue(youToday(old, clock) is YouTodayModel.Steps)
        val gone = snapshot(member("priya", 0)).copy(cheersFrom = listOf("left"))
        assertTrue(youToday(gone, clock) is YouTodayModel.Steps)
    }

    @Test
    fun yesterdaysStepsAreNotShownAsTodays() {
        val stale = youToday(snapshot().copy(stepsDate = "2026-09-29"), clock) as YouTodayModel.Steps
        assertNull(stale.steps)
        assertEquals(YouTodayModel.Steps(6_240, 7_500), youToday(snapshot(), clock))
        assertEquals(YouTodayModel.Connect, youToday(snapshot().copy(todaySteps = null), clock))
    }
}
