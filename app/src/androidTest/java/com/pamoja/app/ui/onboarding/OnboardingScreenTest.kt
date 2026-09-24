package com.pamoja.app.ui.onboarding

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.auth.AuthEntryContent
import com.pamoja.app.ui.theme.PamojaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun firstIntroExplainsTheProductWithoutAskingForAnAccount() {
        var continued = false
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                ProductIntroOneContent(onContinue = { continued = true })
            }
        }

        compose.onNodeWithText("Pamoja").assertIsDisplayed()
        compose.onNodeWithText("Walk further, together.").assertIsDisplayed()
        compose.onNodeWithText("Step tracking").assertIsDisplayed()
        compose.onNodeWithText("Group goals").assertIsDisplayed()
        compose.onNodeWithText("Leaderboard").assertIsDisplayed()
        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
        compose.onNodeWithText("Continue")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.runOnIdle { assertTrue(continued) }
    }

    @Test
    fun secondIntroExplainsTheSharedGoalWithoutAskingForDetails() {
        var continued = false
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                ProductIntroTwoContent(onBack = {}, onContinue = { continued = true })
            }
        }

        compose.onNodeWithText("One goal. Every step counts.").assertIsDisplayed()
        compose.onNodeWithText("48,320").assertIsDisplayed()
        compose.onNodeWithText("Made for different abilities")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
        compose.onNodeWithText("Continue")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.runOnIdle { assertTrue(continued) }
    }

    @Test
    fun signInIsPrimaryAndEveryEntryActionIsWired() {
        var google = false
        var phone = false
        var created = false
        var forgotEmail = ""
        var signedInEmail = ""
        var signedInPassword = ""
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                AuthEntryContent(
                    busyWith = null,
                    error = null,
                    onEmailSignIn = { email, password ->
                        signedInEmail = email
                        signedInPassword = password
                    },
                    onGoogle = { google = true },
                    onPhone = { phone = true },
                    onCreateAccount = { created = true },
                    onForgotPassword = { forgotEmail = it },
                )
            }
        }

        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.onNodeWithText("Private by default").assertDoesNotExist()
        compose.onNodeWithText("Continue with email").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("walker@example.com")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Existing1!")
        compose.onNodeWithText("Sign in")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText("Forgot password?")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText("Create account")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText("Continue with Google")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText("Continue with phone")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText("Walk further, together.").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(google)
            assertTrue(phone)
            assertTrue(created)
            assertTrue(forgotEmail == "walker@example.com")
            assertTrue(signedInEmail == "walker@example.com")
            assertTrue(signedInPassword == "Existing1!")
        }
    }

    @Test
    fun profileAsksOnlyForTheIdentityTheProductNeeds() {
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Dark) {
                ProfileSetupContent(
                    name = "Asha",
                    onNameChange = {},
                    validateName = { if (it.isBlank()) "Enter your name" else null },
                    nameError = null,
                    isLoading = false,
                    isOffline = false,
                    errorBody = null,
                    onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("How should your group know you?").assertIsDisplayed()
        compose.onNodeWithText("This name is visible to people in your groups.").assertIsDisplayed()
        compose.onNodeWithText("Private by default").assertDoesNotExist()
        compose.onNodeWithText("Age").assertDoesNotExist()
        compose.onNodeWithText("Height cm").assertDoesNotExist()
        compose.onNodeWithText("Weight kg").assertDoesNotExist()
    }

    @Test
    fun healthScreenExplainsTheDataBoundaryBeforeRequestingAccess() {
        var requested = false
        var skipped = false
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                HealthConnectContent(
                    permState = PermState.UNKNOWN,
                    onPrimary = { requested = true },
                    onSkip = { skipped = true },
                )
            }
        }

        compose.onNodeWithText("Connect your steps").assertIsDisplayed()
        compose.onNodeWithText("Step count only. No location, route, heart rate or sleep.").assertIsDisplayed()
        compose.onNodeWithText("Connect steps")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText("Skip for now")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.runOnIdle {
            assertTrue(requested)
            assertTrue(skipped)
        }
    }
}
