package com.pamoja.app.ui.home

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.User
import com.pamoja.app.ui.group.GroupUiState
import com.pamoja.app.ui.group.MemberStepData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CelebrationModelTest {

    // A Monday week, 2026-10-05 to 2026-10-11. Friday: four days before today.
    private val friday = LocalDate.parse("2026-10-09")
    private val group = Group(
        groupId = "g",
        weekStartDay = "MONDAY",
        weeklyTarget = 70_000,
        streakWeeks = 5,
        streakThroughWeek = "2026-09-28",
    )

    private fun member(id: String, name: String, walkedDays: Int?) =
        MemberStepData(User(userId = id, name = name), todaySteps = 0, weeklySteps = 0, walkedDays = walkedDays)

    private fun state(vararg members: MemberStepData) = GroupUiState(
        group = group,
        currentUserId = "me",
        memberStepData = members.toList(),
        combinedWeeklySteps = 70_412,
    )

    @Test
    fun `you come first and the rest by name, never by steps`() {
        val model = celebrationModel(
            state(member("z", "Zara Khan", null), member("me", "Ravi", null), member("a", "anil", null)),
            friday,
        )!!
        assertEquals(listOf("Ravi", "anil", "Zara"), model.people.map { it.name })
        assertTrue(model.people.first().isYou)
    }

    @Test
    fun `the streak counts this week, and the days to spare exclude today`() {
        val model = celebrationModel(state(member("me", "Ravi", null)), friday)!!
        assertEquals(6, model.streakWeeks)
        assertEquals(2, model.daysToSpare)
        assertEquals(0, celebrationModel(state(member("me", "Ravi", null)), LocalDate.parse("2026-10-11"))!!.daysToSpare)
    }

    @Test
    fun `a broken streak starts again at one`() {
        val model = celebrationModel(
            state(member("me", "Ravi", null)).copy(group = group.copy(streakThroughWeek = "2026-09-14")),
            friday,
        )!!
        assertEquals(1, model.streakWeeks)
    }

    @Test
    fun `every day means every day before today, and unknown dots never count`() {
        val model = celebrationModel(
            state(
                member("me", "Ravi", 0b0_1111),
                member("m", "Meera", 0b1_1111), // today too: still every day
                member("p", "Priya", 0b0_1011), // missed Wednesday
                member("s", "Sam", null), // older app version: unknown
            ),
            friday,
        )!!
        assertEquals(listOf("me", "m"), model.everyDay.map { it.userId })
        assertFalse(model.everyoneEveryDay)
    }

    @Test
    fun `too early in the week to say anyone walked every day`() {
        val wednesday = LocalDate.parse("2026-10-07")
        val model = celebrationModel(state(member("me", "Ravi", 0b11), member("m", "Meera", 0b11)), wednesday)!!
        assertTrue(model.everyDay.isEmpty())
    }

    @Test
    fun `everyone, when it is everyone`() {
        val model = celebrationModel(state(member("me", "Ravi", 0b1111), member("m", "Meera", 0b1111)), friday)!!
        assertTrue(model.everyoneEveryDay)
    }

    @Test
    fun `nothing to tell without a group`() {
        assertNull(celebrationModel(GroupUiState(), friday))
    }
}
