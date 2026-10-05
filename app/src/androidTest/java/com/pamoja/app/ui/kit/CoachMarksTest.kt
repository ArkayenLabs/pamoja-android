package com.pamoja.app.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoachMarksTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(onDone: () -> Unit, onTap: () -> Unit = {}) {
        compose.setContent {
            KitTheme(dark = false) {
                val state = rememberCoachMarkState()
                CoachMarkHost(state) {
                    Box(Modifier.fillMaxSize()) {
                        Column {
                            PrimaryButton("Week", onClick = onTap, modifier = Modifier.coachMarkTarget("week"))
                            Text("Today", Modifier.coachMarkTarget("today"))
                        }
                        CoachMarkOverlay(
                            state = state,
                            marks = listOf(
                                CoachMark("week", "Your week together", "Everyone's steps add up here."),
                                CoachMark("today", "Who's walked today", "Nudge anyone who hasn't."),
                            ),
                            onDone = onDone,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun stepsAdvanceAndTheLastOneFinishes() {
        var done = 0
        show(onDone = { done++ })

        compose.onNodeWithText("Your week together").assertIsDisplayed()
        compose.onNodeWithText("1 of 2").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Who's walked today").assertIsDisplayed()
        compose.onNodeWithText("2 of 2").assertIsDisplayed()
        // Nothing left to skip on the last step.
        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Got it").performClick()
        compose.runOnIdle { assertEquals(1, done) }
    }

    @Test
    fun skipFinishesAndNothingUnderneathCanBeTapped() {
        var done = 0
        var tapped = false
        show(onDone = { done++ }, onTap = { tapped = true })

        // The overlay takes the tap meant for the button it is lighting.
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithText("Skip").performClick()
        compose.runOnIdle {
            assertFalse(tapped)
            assertEquals(1, done)
        }
    }
}
