package com.pamoja.app.ui.update

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.kit.KitTheme
import com.pamoja.app.ui.theme.PamojaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The gate must work under both themes: it ships in the rebuild (kit) and is
 * meant to be carried to the 1.0 line (terracotta) unchanged.
 */
@RunWith(AndroidJUnit4::class)
class UpdateRequiredScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theGateExplainsItselfAndItsButtonAsksForTheUpdate() {
        var updates = 0
        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Light) {
                UpdateRequiredScreen(onUpdate = { updates++ })
            }
        }

        compose.onNodeWithText("Time to update").assertIsDisplayed()
        compose.onNodeWithText("This version of Pamoja no longer works", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Update").performClick()

        assertEquals(1, updates)
    }

    @Test
    fun theGateRendersUnderTheKitTheme() {
        compose.setContent {
            KitTheme(dark = true) {
                UpdateRequiredScreen(onUpdate = {})
            }
        }

        compose.onNodeWithText("Time to update").assertIsDisplayed()
        compose.onNodeWithText("Update").assertIsDisplayed()
    }
}
