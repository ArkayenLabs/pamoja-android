package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.analytics.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State boundary for the two product-only introduction screens.
 *
 * The completion flag belongs to the installation rather than the account, so
 * it survives sign-out and returning users are not taught the product again.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val analyticsManager: AnalyticsManager,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private var screenViewLogged = false
    private var completionStarted = false

    /** Call from the Composable's LaunchedEffect(Unit) to log the screen view once. */
    fun onScreenViewed() {
        if (!screenViewLogged) {
            screenViewLogged = true
            analyticsManager.logWelcomeScreenViewed()
        }
    }

    fun completeIntro(onComplete: () -> Unit) {
        if (completionStarted) return
        completionStarted = true
        viewModelScope.launch {
            try {
                userPreferences.setIntroSeen(true)
                onComplete()
            } finally {
                // If someone goes back from auth to the second intro screen,
                // Continue must be usable again. This guard only blocks a true
                // double tap while the DataStore write is in flight.
                completionStarted = false
            }
        }
    }
}
