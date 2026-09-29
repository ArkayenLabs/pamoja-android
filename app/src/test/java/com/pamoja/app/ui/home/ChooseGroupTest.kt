package com.pamoja.app.ui.home

import com.pamoja.app.domain.model.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChooseGroupTest {

    private val groups = listOf(Group(groupId = "a"), Group(groupId = "b"))

    @Test
    fun `the remembered group stays, even before the list has caught up`() {
        assertEquals("b", chooseGroup("b", groups, gone = emptySet()))
        // Joined a moment ago: not in the list yet, still shown.
        assertEquals("new", chooseGroup("new", groups, gone = emptySet()))
    }

    @Test
    fun `nothing remembered opens the first group`() {
        assertEquals("a", chooseGroup(null, groups, gone = emptySet()))
    }

    @Test
    fun `a group found gone is never chosen again`() {
        assertEquals("b", chooseGroup("a", groups, gone = setOf("a")))
        assertEquals("b", chooseGroup(null, groups, gone = setOf("a")))
        assertNull(chooseGroup("a", groups, gone = setOf("a", "b")))
    }

    @Test
    fun `no groups means no group`() {
        assertNull(chooseGroup(null, emptyList(), gone = emptySet()))
    }
}
