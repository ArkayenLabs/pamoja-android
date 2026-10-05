package com.pamoja.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.auth.AuthEntryContent
import com.pamoja.app.ui.auth.EmailSignInContent
import com.pamoja.app.ui.group.CreateGroupContent
import com.pamoja.app.ui.kit.KitTheme
import com.pamoja.app.util.InviteLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The onboarding screens rebuilt on the Lilac kit (REBUILD_PLAN.md Phase 3),
 * rendered inside KitTheme as they are in the app.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val groupId = "6413795d-42d0-4f2b-80df-f017a9d32817"

    @Test
    fun startTourHasThreePagesThenGetStarted() {
        var started = false
        compose.setContent {
            KitTheme(dark = false) {
                StartContent(onGetStarted = { started = true }, onHaveInvite = {})
            }
        }

        compose.onNodeWithText("Your people. One weekly goal.").assertIsDisplayed()
        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
        compose.onNodeWithText("Get started").assertDoesNotExist()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Someone noticed your walk").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Your group on your home screen").assertIsDisplayed()
        // The last page has nothing left to skip.
        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Get started").assertHasClickAction().performClick()
        compose.runOnIdle { assertTrue(started) }
    }

    @Test
    fun startCanBeSkippedAndAnInviteNeedsNoTour() {
        var started = false
        var invite = false
        compose.setContent {
            KitTheme(dark = false) {
                StartContent(onGetStarted = { started = true }, onHaveInvite = { invite = true })
            }
        }

        compose.onNodeWithText("I have an invite link").assertHasClickAction().performClick()
        compose.onNodeWithText("Skip").assertHasClickAction().performClick()
        compose.runOnIdle {
            assertTrue(invite)
            assertTrue(started)
        }
    }

    @Test
    fun aPastedInviteLinkIsCheckedBeforeItIsKept() {
        var submitted: String? = null
        compose.setContent {
            KitTheme(dark = false) {
                EnterInviteContent(onSubmit = { submitted = it }, onBack = {})
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("not a link")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("That link doesn't look right. Check it and try again.").assertIsDisplayed()
        compose.runOnIdle { assertNull(submitted) }

        compose.onNode(hasSetTextAction()).performTextReplacement(" ${InviteLink.build(groupId)} ")
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(groupId, submitted) }
    }

    @Test
    fun signInOffersTheSameThreeWaysToEveryone() {
        var google = false
        var phone = false
        var email = false
        compose.setContent {
            KitTheme(dark = false) {
                AuthEntryContent(
                    busyWith = null,
                    error = null,
                    onGoogle = { google = true },
                    onPhone = { phone = true },
                    onEmail = { email = true },
                )
            }
        }

        compose.onNodeWithText("One tap and you're in").assertIsDisplayed()
        compose.onNodeWithText("Welcome back").assertDoesNotExist()
        // Email and password live on their own screen now.
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Continue with Google").performClick()
        compose.onNodeWithText("Continue with phone").performClick()
        compose.onNodeWithText("Continue with email").performClick()
        compose.runOnIdle {
            assertTrue(google)
            assertTrue(phone)
            assertTrue(email)
        }
    }

    @Test
    fun emailSignInWiresEveryAction() {
        var signedIn: Pair<String, String>? = null
        var forgotEmail: String? = null
        var created = false
        compose.setContent {
            KitTheme(dark = false) {
                EmailSignInContent(
                    busy = false,
                    error = null,
                    onSignIn = { e, p -> signedIn = e to p },
                    onCreateAccount = { created = true },
                    onForgotPassword = { forgotEmail = it },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Sign in").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("walker@example.com")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Existing1!")
        compose.onNodeWithText("Sign in").assertIsEnabled().performClick()
        compose.onNodeWithText("Forgot password?").performClick()
        compose.onNodeWithText("New to Pamoja? Create account").performClick()
        compose.runOnIdle {
            assertEquals("walker@example.com" to "Existing1!", signedIn)
            assertEquals("walker@example.com", forgotEmail)
            assertTrue(created)
        }
    }

    /** The Terms require 18+, and this is the one step every new account takes. */
    @Test
    fun theNameStepWaitsForTheAgeConfirmation() {
        var submitted = false
        compose.setContent {
            KitTheme(dark = true) {
                var adult by remember { mutableStateOf(false) }
                ProfileSetupContent(
                    name = "Asha",
                    onNameChange = {},
                    nameError = null,
                    isAdult = adult,
                    onAdultChange = { adult = it },
                    isLoading = false,
                    isOffline = false,
                    errorBody = null,
                    onSubmit = { submitted = true },
                )
            }
        }

        compose.onNodeWithText("Your name").assertIsDisplayed()
        compose.onNodeWithText("Age").assertDoesNotExist()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("I'm 18 or older").performClick()
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(submitted) }
    }

    @Test
    fun startingAGroupShowsItsSettingsFilledWithDefaults() {
        var created: com.pamoja.app.ui.group.NewGroup? = null
        compose.setContent {
            KitTheme(dark = false) {
                CreateGroupContent(
                    isLoading = false,
                    isOffline = false,
                    errorBody = null,
                    onCreate = { created = it },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Weekly goal").assertIsDisplayed()
        compose.onNodeWithText("70,000 steps").assertIsDisplayed()
        compose.onNodeWithText("Member limit").assertIsDisplayed()
        compose.onNodeWithText("Create group").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("  Morning Walkers  ")
        compose.onNodeWithText("Create group").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(
                com.pamoja.app.ui.group.NewGroup(
                    name = "Morning Walkers",
                    weeklyTarget = 70_000,
                    maxMemberCap = 10,
                    weekStart = com.pamoja.app.domain.model.WeekWindow.localeDefault(),
                ),
                created,
            )
        }
    }

    @Test
    fun healthScreenExplainsTheDataBoundaryBeforeRequestingAccess() {
        var requested = false
        var skipped = false
        compose.setContent {
            KitTheme(dark = false) {
                HealthConnectContent(
                    permState = PermState.UNKNOWN,
                    onPrimary = { requested = true },
                    onSkip = { skipped = true },
                )
            }
        }

        compose.onNodeWithText("Connect your steps").assertIsDisplayed()
        compose.onNodeWithText("Step count only. No location, route, heart rate or sleep.").assertIsDisplayed()
        compose.onNodeWithText("Connect steps").assertHasClickAction().performClick()
        compose.onNodeWithText("Skip for now").assertHasClickAction().performClick()
        compose.runOnIdle {
            assertTrue(requested)
            assertTrue(skipped)
        }
    }
}
