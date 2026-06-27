package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.pamoja.app.domain.analytics.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for [WelcomeScreen].
 *
 * Exists solely to log [welcome_screen_viewed] without placing analytics
 * calls inside a Composable. The event fires once per ViewModel lifetime
 * (i.e. once per navigation to the Welcome destination).
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val analyticsManager: AnalyticsManager
) : ViewModel() {

    private var screenViewLogged = false

    /** Call from the Composable's LaunchedEffect(Unit) to log the screen view once. */
    fun onScreenViewed() {
        if (!screenViewLogged) {
            screenViewLogged = true
            analyticsManager.logWelcomeScreenViewed()
        }
    }
}
