package com.pamoja.app.ui.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.auth.AuthEntryContent
import com.pamoja.app.ui.theme.PamojaTheme

/**
 * Debug-only visual review surface for the real onboarding content.
 *
 * It does not fake navigation, authentication or production data. It simply
 * renders each stateless screen boundary so screenshots can be reviewed before
 * a full onboarding run. Release builds do not contain this activity.
 */
class OnboardingPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getIntExtra(EXTRA_SCREEN, 1).coerceIn(1, 5)
        val dark = intent.getBooleanExtra(EXTRA_DARK, false)

        setContent {
            PamojaTheme(
                themePreference = if (dark) ThemePreference.Dark else ThemePreference.Light,
            ) {
                when (screen) {
                    1 -> ProductIntroOneContent(onContinue = {})
                    2 -> ProductIntroTwoContent(onBack = {}, onContinue = {})
                    3 -> AuthEntryContent(
                        busyWith = null,
                        error = null,
                        onEmailSignIn = { _, _ -> },
                        onGoogle = {},
                        onPhone = {},
                        onCreateAccount = {},
                        onForgotPassword = {},
                        onBack = {},
                    )
                    4 -> ProfileSetupContent(
                        name = "",
                        onNameChange = {},
                        validateName = { value -> value.takeIf { it.isBlank() }?.let { "Enter your name" } },
                        // Mirrors the untouched production screen: the field is
                        // neutral, while Continue stays disabled until a valid
                        // name is entered.
                        nameError = "Enter your name",
                        isLoading = false,
                        isOffline = false,
                        errorBody = null,
                        onSubmit = {},
                    )
                    else -> HealthConnectContent(
                        permState = PermState.UNKNOWN,
                        onPrimary = {},
                        onSkip = {},
                    )
                }
            }
        }
    }

    private companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_DARK = "dark"
    }
}
