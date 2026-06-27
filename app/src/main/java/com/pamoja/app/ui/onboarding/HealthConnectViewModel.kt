package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.analytics.AnalyticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    val healthConnectReader: HealthConnectReader,
    val userPreferences: UserPreferences,
    private val analyticsManager: AnalyticsManager
) : ViewModel() {

    private var screenViewLogged = false
    private var permissionRequestLogged = false

    /** Call when the HealthConnectScreen enters composition. Logs once per session. */
    fun onScreenViewed() {
        if (!screenViewLogged) {
            screenViewLogged = true
            analyticsManager.logHealthConnectScreenViewed()
        }
    }

    /** Call when the user taps "Connect Health Connect" to launch the permission dialog. */
    fun onPermissionRequested() {
        if (!permissionRequestLogged) {
            permissionRequestLogged = true
            analyticsManager.logHealthConnectPermissionRequested()
        }
    }

    /** Called when the user grants Health Connect step-read permission. */
    fun onPermissionGranted(userId: String) {
        analyticsManager.logHealthConnectPermissionGranted(userId)
    }

    /** Called when the user denies Health Connect permission in the system dialog. */
    fun onPermissionDenied() {
        analyticsManager.logHealthConnectPermissionDenied()
    }

    /** Called when the user taps "Skip for now" or "Continue without steps". */
    fun onSkipped() {
        analyticsManager.logHealthConnectSkipped()
    }
}