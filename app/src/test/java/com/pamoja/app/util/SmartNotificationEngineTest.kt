package com.pamoja.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class SmartNotificationEngineTest {

    @Test
    fun `first sync with steps says they are in, for the real group`() {
        val notice = SmartNotificationEngine.select(context(userStepsToday = 2_146))

        assertEquals(NotificationCategory.ACHIEVEMENT, notice?.category)
        assertEquals(LocalMessage.StepsIn(2_146, "bama"), notice?.message)
        assertEquals("group-1", notice?.groupId)
    }

    @Test
    fun `first sync before any walking says the setup worked`() {
        val notice = SmartNotificationEngine.select(context(userStepsToday = 0))

        assertEquals(LocalMessage.AllSet("bama"), notice?.message)
    }

    @Test
    fun `every later sync stays silent`() {
        assertNull(SmartNotificationEngine.select(context(isFirstEverSync = false)))
    }

    @Test
    fun `a muted channel and quiet hours are both respected`() {
        assertNull(
            SmartNotificationEngine.select(
                context(mutedCategories = setOf(NotificationCategory.ACHIEVEMENT)),
            ),
        )
        assertNull(SmartNotificationEngine.select(context(now = LocalTime.of(23, 30))))
    }

    @Test
    fun `quiet hours wrap past midnight but a daytime window does not`() {
        val night = LocalTime.of(22, 0) to LocalTime.of(8, 0)
        assertEquals(true, isQuietHours(LocalTime.of(2, 0), night.first, night.second))
        assertEquals(false, isQuietHours(LocalTime.of(12, 0), night.first, night.second))

        val day = LocalTime.of(9, 0) to LocalTime.of(17, 0)
        assertEquals(true, isQuietHours(LocalTime.of(12, 0), day.first, day.second))
        assertEquals(false, isQuietHours(LocalTime.of(20, 0), day.first, day.second))
    }

    private fun context(
        userStepsToday: Long = 1_000,
        isFirstEverSync: Boolean = true,
        now: LocalTime = LocalTime.of(17, 0),
        mutedCategories: Set<NotificationCategory> = emptySet(),
    ) = NotificationContext(
        groupId = "group-1",
        groupName = "bama",
        userStepsToday = userStepsToday,
        isFirstEverSync = isFirstEverSync,
        now = now,
        mutedCategories = mutedCategories,
    )
}
