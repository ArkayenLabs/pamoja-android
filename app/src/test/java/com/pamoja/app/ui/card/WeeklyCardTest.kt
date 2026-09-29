package com.pamoja.app.ui.card

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.WeeklyAward
import com.pamoja.app.util.InviteLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyCardTest {

    // Thursday, so four days of a Monday week have begun.
    private val thursday = LocalDate.of(2026, 10, 1)
    private val monday = "2026-09-28"

    private val group = Group(
        groupId = "6413795d-42d0-4f2b-80df-f017a9d32817",
        name = "Sharma Family",
        weeklyTarget = 20_000,
        weekStartDay = DayOfWeek.MONDAY.name,
        streakWeeks = 5,
        streakThroughWeek = "2026-09-21",
    )

    private fun member(id: String, name: String, steps: Long, week: String = monday, days: Int = 0, daysWeek: String = monday) =
        Membership(
            userId = id, groupId = group.groupId, displayName = name,
            weeklySteps = steps, weekStart = week, walkedDays = days, walkedWeekStart = daysWeek,
        )

    @Test
    fun onlyThisWeeksFiguresCountAndTheStreakTicksUpOnceTheGoalIsMet() {
        val card = weeklyCardOf(
            group,
            listOf(
                member("priya", "Priya Sharma", 15_000, days = 0b1111),
                member("dad", "Dad", 6_000, days = 0b0111),
                // Last week's figure and mask: counted as nothing, earning nothing.
                member("old", "Old Sync", 90_000, week = "2026-09-21", days = 0b1111111, daysWeek = "2026-09-21"),
            ),
            today = thursday,
        )

        assertEquals(21_000L, card.total)
        assertTrue(card.reached)
        assertEquals(6, card.streak)
        assertEquals(listOf("Priya", "Dad", "Old"), card.members.map { it.name })
        assertEquals(
            listOf("priya" to WeeklyAward.MOST_STEPS, "dad" to WeeklyAward.EVERY_DAY),
            card.awards.map { (member, award) -> member.userId to award },
        )
        assertEquals(4 + 3, card.walks)
    }

    @Test
    fun aStreakBrokenByAnUnfinalizedWeekIsNotShown() {
        val card = weeklyCardOf(group.copy(streakThroughWeek = "2026-09-14"), emptyList(), today = thursday)

        assertEquals(0, card.streak)
        assertFalse(card.reached)
    }

    @Test
    fun theInstallReferrerCarriesOnlyAWellFormedGroupId() {
        val code = "6413795d-42d0-4f2b-80df-f017a9d32817"

        assertEquals(code, InviteLink.codeFromInstallReferrer("utm_source=invite_link&invite=$code"))
        assertEquals(code, InviteLink.codeFromInstallReferrer("invite=$code"))
        assertNull(InviteLink.codeFromInstallReferrer("utm_source=google-play&utm_medium=organic"))
        assertNull(InviteLink.codeFromInstallReferrer("invite=../../groups/x"))
        assertNull(InviteLink.codeFromInstallReferrer(null))
    }

    @Test
    fun theCardLinksToTheStoreNeverToTheGroup() {
        val link = WeeklyCardShare.appLink()

        assertTrue(link.startsWith("https://play.google.com/store/apps/details?id="))
        assertFalse(link.contains(group.groupId))
    }
}
