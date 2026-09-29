package com.pamoja.app.ui.group

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.kit.KitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group details rebuilt on the Lilac kit (REBUILD_PLAN.md Phase 4), rendered
 * inside KitTheme as it is in the app. Replaces the
 * test of the old sponsorship card, which this screen's Premium card replaced.
 */
@RunWith(AndroidJUnit4::class)
class GroupDetailsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val group = Group(
        groupId = "6413795d-42d0-4f2b-80df-f017a9d32817",
        name = "Journey walkers",
        adminId = ADMIN,
        weeklyTarget = 70_000,
        maxMemberCap = 10,
        memberCount = 2,
        weekStartDay = "MONDAY",
    )

    private val members = listOf(
        Membership(userId = ADMIN, groupId = group.groupId, displayName = "Asha", role = "admin"),
        Membership(userId = MEMBER, groupId = group.groupId, displayName = "Ravi", joinedAt = 1_725_000_000_000L),
    )

    @Test
    fun aMemberCanLeaveButNotEditAndSponsoringOpensThePaywall() {
        var paywall = 0
        var left = 0
        setContent(currentUserId = MEMBER, onOpenPaywall = { paywall++ }, onLeave = { left++ })

        compose.onNodeWithText("Members").assertIsDisplayed()
        compose.onNodeWithText("Group settings").assertDoesNotExist()
        compose.onNodeWithText("Delete group").assertDoesNotExist()

        compose.onNodeWithText("Get Premium for this group").performScrollTo().performClick()
        compose.onNodeWithText("Leave group").performScrollTo().performClick()
        compose.onNode(hasText("Leave") and hasAnyAncestor(isDialog())).performClick()

        compose.runOnIdle {
            assertEquals(1, paywall)
            assertEquals(1, left)
        }
    }

    @Test
    fun theAdminChangesTheGoalFromItsSheet() {
        var saved: Int? = null
        setContent(currentUserId = ADMIN, onSaveGoal = { saved = it })

        compose.onNodeWithText("Weekly goal").performScrollTo().performClick()
        compose.onNodeWithText("100,000 steps").performClick()

        compose.runOnIdle { assertEquals(100_000, saved) }
    }

    @Test
    fun deletingNeedsTheGroupNameTyped() {
        var deleted = false
        setContent(currentUserId = ADMIN, onDelete = { deleted = true })

        compose.onNodeWithText("Delete group").performScrollTo().performClick()
        val confirm = compose.onNode(hasText("Delete group") and hasAnyAncestor(isDialog()))
        confirm.assertIsNotEnabled()

        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Journey walkers")
        confirm.assertIsEnabled().performClick()

        compose.runOnIdle { assertTrue(deleted) }
    }

    private fun setContent(
        currentUserId: String,
        onOpenPaywall: () -> Unit = {},
        onSaveGoal: (Int) -> Unit = {},
        onLeave: () -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        compose.setContent {
            KitTheme(dark = false) {
                GroupDetailsContent(
                    state = GroupDetailsUiState(
                        group = group,
                        members = members,
                        currentUserId = currentUserId,
                        access = GroupAccessState.Free,
                    ),
                    onBack = {},
                    onInvite = {},
                    onOpenReview = {},
                    onOpenPaywall = onOpenPaywall,
                    onSaveName = {},
                    onSaveGoal = onSaveGoal,
                    onSaveCap = {},
                    onSaveWeekStart = {},
                    onSettingsSavedShown = {},
                    onChoosePhoto = {},
                    onRemovePhoto = {},
                    onRemoveMember = {},
                    onLeave = onLeave,
                    onDelete = onDelete,
                    onRetry = {},
                    onActionErrorShown = {},
                    subscriptionSalesEnabled = true,
                )
            }
        }
    }

    private companion object {
        const val ADMIN = "admin-uid"
        const val MEMBER = "member-uid"
    }
}
