package com.pamoja.app.ui.weeklyreview

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupAccess
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupFeature
import com.pamoja.app.domain.model.GroupPreview
import com.pamoja.app.domain.model.GroupWeekContribution
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.theme.PamojaTheme
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class WeeklyReviewScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var originalLocale: Locale

    @Before
    fun useStableDateFormatting() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun dayOnePaidOrganizerCanPlanWithoutHistoryFinishing() {
        var saved = false
        val group = Group(groupId = "new-group", name = "Sunday Circle Friends Across Many Cities",
            adminId = "organizer", weeklyTarget = 100_000)
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                WeeklyReviewContent(
                    uiState = WeeklyReviewUiState(
                        group = group, dayOnePlanningEnabled = true, currentUserId = "organizer",
                        historyLoaded = false, nextWeekPlanLoaded = true, nextWeekStart = "2026-09-14",
                        groupAccess = GroupAccessState.Premium(GroupAccess("new-group",
                            setOf(GroupFeature.CircleV1), Long.MAX_VALUE)),
                    ),
                    planningOnly = true, onBack = {}, onContinue = {}, onRetry = {},
                    onSaveNextWeekPlan = { choice, _ -> saved = choice == com.pamoja.app.domain.model.NextWeekPlanChoice.Gentler },
                )
            }
        }
        compose.onNodeWithText(group.name).assertIsDisplayed()
        compose.onNodeWithText("Gentler").performScrollTo().performClick()
        compose.onNodeWithText("Next week: 80,000 steps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Schedule next week").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(saved) }
    }

    @Test
    fun savedPromiseRemainsVisibleWithoutPaidEditingControls() {
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                WeeklyReviewContent(
                    uiState = WeeklyReviewUiState(
                        group = Group(groupId = "g", name = "Sunday Circle"),
                        dayOnePlanningEnabled = true, groupAccess = GroupAccessState.Free,
                        scheduledGoal = com.pamoja.app.domain.model.ScheduledGoal(
                            "2026-09-14", 60_000, "Asia/Kolkata", "scheduled"),
                    ), planningOnly = true, onBack = {}, onContinue = {}, onRetry = {},
                )
            }
        }
        compose.onNodeWithText("Saved goal: 60,000 steps", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Schedule next week").assertCountEquals(0)
    }

    @Test
    fun populatedReviewStatesResultsInWordsAndNeverShowsUserIds() {
        val group = Group(
            groupId = "group-1",
            name = "Morning walkers",
            adminId = "member-a",
            weeklyTarget = 70_000,
            createdAt = LocalDate.of(2026, 8, 1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        val state = WeeklyReviewUiState(
            isLoading = false,
            group = group,
            historyLoaded = true,
            currentMembers = mapOf(
                "member-a" to WeeklyReviewMember("Asha", null),
                "member-b" to WeeklyReviewMember("Ben", null),
            ),
            weeks = listOf(
                week("2026-08-17", total = 84_000L, target = 70_000L),
                week("2026-08-10", total = 60_000L, target = 70_000L),
            ),
        )
        var wentBack = false

        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                WeeklyReviewContent(
                    uiState = state,
                    onBack = { wentBack = true },
                    onContinue = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Weekly review").assertIsDisplayed()
        compose.onNodeWithText("Morning walkers").assertIsDisplayed()
        compose.onNodeWithText("LAST WEEK").assertIsDisplayed()
        compose.onNodeWithText("GOAL REACHED").assertIsDisplayed()
        compose.onNodeWithText("84,000").assertIsDisplayed()
        compose.onNodeWithText("Recent progress").assertIsDisplayed()
        compose.onNodeWithText("Best 84,000").assertIsDisplayed()
        compose.onNodeWithText("Earlier weeks").assertIsDisplayed()
        compose.onNodeWithText("Aug 10 – Aug 16 · Goal missed").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Top contributors: Asha, Ben")
            .assertCountEquals(2)
        compose.onNodeWithText("member-a").assertDoesNotExist()
        compose.onNodeWithText("24,000 more steps than the previous week").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Week of Aug 10, 60,000 steps. Tap for details.",
        )
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText("60,000 steps").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back")
            .assertHasClickAction()
            .performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun emptyReviewExplainsWhenTheFirstResultWillAppear() {
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Dark) {
                WeeklyReviewContent(
                    uiState = WeeklyReviewUiState(
                        isLoading = false,
                        group = Group(groupId = "group-1", name = "New circle"),
                        historyLoaded = true,
                    ),
                    onBack = {},
                    onContinue = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Your first review comes after this week").assertIsDisplayed()
        compose.onNodeWithText(
            "Keep walking together. When the week closes, the result will stay here instead of disappearing.",
        ).assertIsDisplayed()
    }

    @Test
    fun missedWeekOffersAKindFreshStart() {
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Dark) {
                WeeklyReviewContent(
                    uiState = WeeklyReviewUiState(
                        isLoading = false,
                        group = Group(
                            groupId = "group-1",
                            name = "New circle",
                            weeklyTarget = 70_000,
                        ),
                        historyLoaded = true,
                        weeks = listOf(week("2026-08-17", total = 60_000L, target = 70_000L)),
                    ),
                    onBack = {},
                    onContinue = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("LAST WEEK").assertIsDisplayed()
        compose.onNodeWithText("GOAL MISSED").assertIsDisplayed()
        compose.onNodeWithText("60,000").assertIsDisplayed()
    }

    @Test
    fun previewPendingExplainsTheMeaningfulWeekTriggerWithoutAStoreAction() {
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                WeeklyReviewContent(
                    uiState = WeeklyReviewUiState(
                        isLoading = false,
                        group = Group(groupId = "group-1", name = "New circle"),
                        circlePreviewEnabled = true,
                        groupAccess = GroupAccessState.Free,
                    ),
                    onBack = {},
                    onContinue = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Your Circle Preview starts after an active week")
            .assertIsDisplayed()
        compose.onNodeWithText(
            "When at least two members record steps in the same completed week, everyone in this group gets 14 days of Weekly Review.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Continue this week").assertDoesNotExist()
    }

    @Test
    fun activePreviewShowsTheSharedEndDateAboveHistory() {
        var openedPremium = false
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                WeeklyReviewContent(
                    uiState = WeeklyReviewUiState(
                        isLoading = false,
                        group = Group(groupId = "group-1", name = "Morning walkers"),
                        historyLoaded = true,
                        weeks = listOf(week("2026-08-17", total = 84_000L, target = 70_000L)),
                        circlePreviewEnabled = true,
                        groupAccess = GroupAccessState.Preview(
                            preview(validUntil = epochMillis(2026, 9, 13)),
                        ),
                    ),
                    onBack = {},
                    onContinue = {},
                    onRetry = {},
                    onOpenPremium = { openedPremium = true },
                )
            }
        }

        compose.onNodeWithText(
            "Circle Preview available until Sep 13, 2026",
        ).assertIsDisplayed()
        compose.onNodeWithText("GOAL REACHED").assertIsDisplayed()
        compose.onNodeWithText("Next week together").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unlock with Premium").performClick()
        compose.runOnIdle { assertTrue(openedPremium) }
    }

    @Test
    fun oldPremiumLeaseLetsOrganizerScheduleNextWeekWithoutRepurchase() {
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Dark) {
                WeeklyReviewContent(
                    uiState = WeeklyReviewUiState(
                        isLoading = false,
                        group = Group(
                            groupId = "group-1",
                            name = "Morning walkers",
                            adminId = "member-a",
                            weeklyTarget = 70_000,
                        ),
                        currentUserId = "member-a",
                        historyLoaded = true,
                        weeks = listOf(week("2026-08-17", total = 84_000L, target = 70_000L)),
                        circlePreviewEnabled = true,
                        groupAccess = GroupAccessState.Premium(
                            GroupAccess(
                                groupId = "group-1",
                                featureSet = setOf(GroupFeature.CircleV1),
                                validUntilMillis = Long.MAX_VALUE,
                            ),
                        ),
                        nextWeekPlanLoaded = true,
                        nextWeekStart = "2026-08-24",
                    ),
                    onBack = {},
                    onContinue = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Set the group’s next week").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Schedule next week").assertHasClickAction()
    }

    @Test
    fun endedPreviewKeepsTheGroupSafeAndOffersCurrentWeek() {
        var continued = false
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Dark) {
                WeeklyReviewContent(
                    uiState = WeeklyReviewUiState(
                        isLoading = false,
                        group = Group(groupId = "group-1", name = "Morning walkers"),
                        circlePreviewEnabled = true,
                        groupAccess = GroupAccessState.PreviewExpired(
                            preview(validUntil = epochMillis(2026, 8, 30)),
                        ),
                    ),
                    onBack = {},
                    onContinue = { continued = true },
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Your Circle Preview has ended").assertIsDisplayed()
        compose.onNodeWithText(
            "Your group, members, and current-week progress are still here. Weekly Review will reopen with Pamoja Premium.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Continue this week").performClick()
        compose.runOnIdle { assertTrue(continued) }
    }

    private fun preview(validUntil: Long) = GroupPreview(
        groupId = "group-1",
        featureSet = setOf(GroupFeature.CircleV1),
        eligibleWeekStart = "2026-08-17",
        startedAtMillis = epochMillis(2026, 8, 24),
        validUntilMillis = validUntil,
    )

    private fun epochMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun week(weekStart: String, total: Long, target: Long) = GroupWeekSummary(
        groupId = "group-1",
        weekStart = weekStart,
        weekEnd = LocalDate.parse(weekStart).plusDays(6).toString(),
        targetSteps = target,
        totalSteps = total,
        memberCount = 2,
        activeMemberCount = 2,
        contributions = listOf(
            GroupWeekContribution("member-a", total - 20_000L),
            GroupWeekContribution("member-b", 20_000L),
        ),
        finalizedAtEpochMillis = 1L,
    )
}
