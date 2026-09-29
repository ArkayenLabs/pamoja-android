package com.pamoja.app.ui.paywall

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.SponsorshipFailure
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.theme.PamojaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaywallScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /** The exact group, and who else it reaches, before any price or button. */
    @Test
    fun thePaywallNamesTheExactGroupAndItsPeopleBeforeCheckout() {
        val exactName = "Asha & Ben's Sunrise Walking Circle"
        val groupId = "6413795d-42d0-4f2b-80df-f017a9d32817"

        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Dark) {
                PaywallContent(
                    state = PaywallUiState(
                        group = Group(groupId = groupId, name = exactName),
                        purchaseContext = com.pamoja.app.domain.repository.PurchaseContext(
                            groupId = groupId, payerUid = "me", groupIsPremium = false, isGroupSponsor = false,
                            subscriptionIsActive = false, sponsoredGroupId = null, sponsoredGroupName = null,
                        ),
                        isGroupLoading = false,
                        members = listOf(
                            com.pamoja.app.domain.model.Membership(userId = "me", displayName = "Me"),
                            com.pamoja.app.domain.model.Membership(userId = "asha", displayName = "Asha Rao"),
                            com.pamoja.app.domain.model.Membership(userId = "ben", displayName = "Ben"),
                        ),
                    ),
                    onDismiss = {}, onRetryContext = {}, onSelectPlan = {}, onPurchase = {}, onKeepBoth = {},
                    onMoveHere = {}, onRestore = {}, onRetryVerification = {}, onRefresh = {}, onManageInPlay = {},
                )
            }
        }

        compose.onNodeWithText("Premium for $exactName").assertIsDisplayed()
        // The payer is never listed as someone else who gets it.
        compose.onNodeWithText("Asha and Ben get it too. One of you pays.").assertIsDisplayed()
    }

    @Test
    fun paymentVerificationNamesTheGroupAndDoesNotClaimAccessYet() {
        val exactName = "Asha's Sunrise Walkers"

        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                SponsorshipVerificationNotice(
                    groupName = exactName,
                    isVerifying = true,
                    hasUnconfirmedPurchase = true,
                    error = null,
                    onRetry = {},
                    onMove = {},
                )
            }
        }

        compose.onNodeWithText("Payment received").assertIsDisplayed()
        compose.onNodeWithText(
            "Confirming Premium for $exactName. Keep this screen open for a moment."
        ).assertIsDisplayed()
    }

    @Test
    fun delayedVerificationOffersRetryWithoutAnotherPurchase() {
        var retries = 0
        val exactName = "Family Steps"

        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Dark) {
                SponsorshipVerificationNotice(
                    groupName = exactName,
                    isVerifying = false,
                    hasUnconfirmedPurchase = true,
                    error = AppError.Sponsorship(
                        SponsorshipFailure.NoActiveSubscription
                    ),
                    onRetry = { retries += 1 },
                    onMove = {},
                )
            }
        }

        compose.onNodeWithText("Your payment is safe").assertIsDisplayed()
        compose.onNodeWithText("Verify access").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun assignmentConflictOffersAnExplicitMoveAction() {
        var moves = 0

        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                SponsorshipVerificationNotice(
                    groupName = "Family Steps",
                    isVerifying = false,
                    hasUnconfirmedPurchase = true,
                    error = AppError.Sponsorship(
                        SponsorshipFailure.SubscriptionAssignedElsewhere
                    ),
                    onRetry = {},
                    onMove = { moves += 1 },
                )
            }
        }

        compose.onNodeWithText("Move Premium here").performClick()
        compose.runOnIdle { assertEquals(1, moves) }
    }

    @Test
    fun completedPurchaseNeverClaimsNothingWasChargedWhenGroupChanged() {
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Dark) {
                SponsorshipVerificationNotice(
                    groupName = "Family Steps",
                    isVerifying = false,
                    hasUnconfirmedPurchase = true,
                    error = AppError.Sponsorship(SponsorshipFailure.GroupUnavailable),
                    onRetry = {},
                    onMove = {},
                )
            }
        }

        compose.onNodeWithText("Premium needs another group").assertIsDisplayed()
    }
}
