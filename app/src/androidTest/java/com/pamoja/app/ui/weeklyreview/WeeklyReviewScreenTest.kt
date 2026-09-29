package com.pamoja.app.ui.weeklyreview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupAccess
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupFeature
import com.pamoja.app.domain.model.GroupWeekContribution
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.kit.KitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The rebuilt review: last week for everyone, the rest with Premium. */
@RunWith(AndroidJUnit4::class)
class WeeklyReviewScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val groupId = "6413795d-42d0-4f2b-80df-f017a9d32817"
    private val group = Group(groupId = groupId, name = "Morning walkers", weeklyTarget = 70_000, streakThroughWeek = "2026-09-21")

    private fun week(start: String, total: Long, shielded: Boolean = false) = GroupWeekSummary(
        groupId = groupId,
        weekStart = start,
        weekEnd = java.time.LocalDate.parse(start).plusDays(6).toString(),
        targetSteps = 70_000,
        totalSteps = total,
        memberCount = 1,
        activeMemberCount = 1,
        contributions = listOf(GroupWeekContribution("asha", total, walkedDays = 0b1111111)),
        finalizedAtEpochMillis = 1L,
        shielded = shielded,
    )

    private fun show(state: WeeklyReviewUiState, onOpenPremium: () -> Unit = {}, onSelectWeek: (String) -> Unit = {}) {
        compose.setContent {
            KitTheme(dark = false) {
                WeeklyReviewContent(state, onBack = {}, onOpenPremium = onOpenPremium, onSelectWeek = onSelectWeek, onRetry = {})
            }
        }
    }

    @Test
    fun aFreeGroupSeesLastWeekAndTheWayToTheRest() {
        var opened = false
        show(
            WeeklyReviewUiState(
                isLoading = false,
                group = group,
                access = GroupAccessState.Free,
                lastWeek = week("2026-09-21", 84_000),
                lastWeekLoaded = true,
                members = mapOf("asha" to WeeklyReviewMember("Asha", null)),
            ),
            onOpenPremium = { opened = true },
        )

        compose.onNodeWithText("Last week").assertIsDisplayed()
        compose.onNodeWithText("Goal met").assertIsDisplayed()
        compose.onNodeWithText("Asha").assertIsDisplayed()
        compose.onNodeWithText("Every week before last").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("See Premium").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    /** A covered miss is said as what it was, not as a failure. */
    @Test
    fun aShieldedWeekSaysAShieldWasUsed() {
        show(
            WeeklyReviewUiState(
                isLoading = false,
                group = group,
                access = GroupAccessState.Free,
                lastWeek = week("2026-09-21", 51_000, shielded = true),
                lastWeekLoaded = true,
            ),
        )

        compose.onNodeWithText("Shield used").assertIsDisplayed()
    }

    @Test
    fun aGroupWithNoFinishedWeekYetSaysWhenItWillAppear() {
        show(WeeklyReviewUiState(isLoading = false, group = group.copy(streakThroughWeek = ""), access = GroupAccessState.Free, lastWeekLoaded = true))

        compose.onNodeWithText("Your first week is still going").assertIsDisplayed()
    }

    @Test
    fun withPremiumEveryWeekIsARowThatOpensIt() {
        val selected = mutableListOf<String>()
        show(
            WeeklyReviewUiState(
                isLoading = false,
                group = group,
                access = GroupAccessState.Premium(GroupAccess(groupId, setOf(GroupFeature.CircleV1), Long.MAX_VALUE)),
                lastWeek = week("2026-09-21", 84_000),
                lastWeekLoaded = true,
                history = listOf(week("2026-09-21", 84_000), week("2026-09-14", 40_000)),
            ),
            onSelectWeek = { selected += it },
        )

        compose.onNodeWithContentDescription("14 Sep to 20 Sep, 40,000 steps, goal missed", substring = true)
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(listOf("2026-09-14"), selected) }
    }
}
