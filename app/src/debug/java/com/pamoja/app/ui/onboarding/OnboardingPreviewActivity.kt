package com.pamoja.app.ui.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.auth.AuthEntryContent
import com.pamoja.app.ui.auth.EmailSignInContent
import com.pamoja.app.ui.group.CreateGroupContent
import com.pamoja.app.ui.kit.KitTheme

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
        val screen = intent.getIntExtra(EXTRA_SCREEN, 1).coerceIn(1, 7)
        val dark = intent.getBooleanExtra(EXTRA_DARK, false)

        setContent {
            KitTheme(dark = dark) {
                when (screen) {
                    1 -> StartContent(onGetStarted = {}, onHaveInvite = {})
                    2 -> EnterInviteContent(onSubmit = {}, onBack = {})
                    3 -> AuthEntryContent(busyWith = null, error = null, onGoogle = {}, onPhone = {}, onEmail = {}, onBack = {})
                    4 -> EmailSignInContent(
                        busy = false, error = null, onSignIn = { _, _ -> },
                        onCreateAccount = {}, onForgotPassword = {}, onBack = {},
                    )
                    5 -> ProfileSetupContent(
                        name = "Ravi Sharma", onNameChange = {}, nameError = null,
                        isAdult = false, onAdultChange = {},
                        isLoading = false, isOffline = false, errorBody = null, onSubmit = {},
                        onChoosePhoto = {},
                    )
                    6 -> CreateGroupContent(isLoading = false, isOffline = false, errorBody = null, onCreate = {}, onBack = {})
                    else -> HealthConnectContent(permState = PermState.UNKNOWN, onPrimary = {}, onSkip = {})
                }
            }
        }
    }

    private companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_DARK = "dark"
    }
}
