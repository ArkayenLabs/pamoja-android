package com.pamoja.app.ui.group

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.theme.PamojaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupSponsorshipEntryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sponsorshipEntryExplainsSharedPremiumAndOpensPaywall() {
        var opens = 0

        compose.setContent {
            PamojaTheme(themePreference = ThemePreference.Dark) {
                GroupSponsorshipEntryCard(onClick = { opens += 1 })
            }
        }

        compose.onNodeWithText("Sponsor this group").assertIsDisplayed()
        compose.onNodeWithText(
            "One person subscribes. Everyone in this group gets Pamoja Premium."
        ).assertIsDisplayed()
        compose.onNodeWithText("Sponsor this group").performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }
}
