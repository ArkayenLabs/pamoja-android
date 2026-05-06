package com.pamoja.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.FirebaseAuth
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.ui.PamojaNavGraph
import com.pamoja.app.ui.Screen
import com.pamoja.app.ui.theme.PamojaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Session strategy (single source of truth = FirebaseAuth):
 *
 *  • Firebase SDK persists the anonymous auth token across app restarts, reinstalls,
 *    and DataStore clears — so auth.currentUser is the authoritative source.
 *
 *  • DataStore isOnboarded is used ONLY as a "has the user filled their profile?" flag.
 *    It is intentionally NOT used as the auth gate any more.
 *
 *  Decision tree at launch:
 *    1. FirebaseAuth.currentUser == null  → Welcome (first-time user, must onboard)
 *    2. FirebaseAuth.currentUser != null  → Home    (returning user, session lives on)
 *
 *  This means a user who clears app data keeps their Firebase UID and Firestore data,
 *  goes directly to Home, and never sees the onboarding again.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── Play In-App Update (Flexible) ────────────────────────────────────
        // Checks if a newer version is available on Play Store.
        // If yes: downloads silently in background, then prompts user to restart.
        // Has zero effect when installed via direct APK (only works on Play installs).
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.startUpdateFlow(
                    info,
                    this,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            }
        }
        setContent {
            PamojaTheme {
                val navController = rememberNavController()

                // Determine start destination synchronously from FirebaseAuth — no loading flash.
                // Firebase keeps the token in its own encrypted storage which survives DataStore
                // clears and app updates, so currentUser is reliable immediately at launch.
                var startDestination by remember {
                    mutableStateOf(
                        if (firebaseAuth.currentUser != null) Screen.Home.route
                        else Screen.Welcome.route
                    )
                }

                // If a Firebase user exists but DataStore has been cleared (e.g. after a backup
                // restore), re-hydrate the local preferences so the rest of the app works normally.
                LaunchedEffect(Unit) {
                    val firebaseUser = firebaseAuth.currentUser
                    if (firebaseUser != null) {
                        // Ensure DataStore reflects reality — idempotent, safe to call every launch
                        userPreferences.saveUserId(firebaseUser.uid)
                        userPreferences.setOnboarded(true)
                    }
                }

                PamojaNavGraph(
                    navController    = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}