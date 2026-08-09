package com.pamoja.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
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
import com.pamoja.app.util.InviteLink
import com.pamoja.app.util.SmartNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the app was asked to open. Null means an ordinary launch. */
private sealed interface LaunchTarget {
    /** Notification tap. Open this group directly. */
    data class OpenGroup(val groupId: String) : LaunchTarget

    /** Invite link or QR scan. Join using this code. */
    data class Invite(val code: String) : LaunchTarget
}

/**
 * Session strategy (single source of truth = FirebaseAuth):
 *
 *  - The Firebase SDK persists the anonymous auth token across app restarts,
 *    reinstalls and DataStore clears, so auth.currentUser is authoritative.
 *
 *  - DataStore isOnboarded is only a "has the user filled their profile" flag.
 *    It is intentionally not used as the auth gate.
 *
 *  Decision tree at launch:
 *    1. FirebaseAuth.currentUser == null  -> Welcome (first run, must onboard)
 *    2. FirebaseAuth.currentUser != null  -> Home    (returning user)
 *
 *  So a user who clears app data keeps their Firebase UID and Firestore data,
 *  goes straight to Home, and never sees onboarding again.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var firebaseAuth: FirebaseAuth

    /** Set by onCreate and onNewIntent, consumed once by the composition. */
    private val launchTarget = MutableStateFlow<LaunchTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        // Play In-App Update (flexible). Downloads in the background, then
        // prompts for a restart. No effect when installed via direct APK.
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
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

                // Resolved synchronously so there is no loading flash. Firebase
                // keeps the token in its own storage, so currentUser is reliable
                // immediately at launch.
                var startDestination by remember {
                    mutableStateOf(
                        if (firebaseAuth.currentUser != null) Screen.Home.route
                        else Screen.Welcome.route
                    )
                }

                // If a Firebase user exists but DataStore was cleared, for
                // example after a backup restore, rehydrate local preferences.
                LaunchedEffect(Unit) {
                    firebaseAuth.currentUser?.let { user ->
                        userPreferences.saveUserId(user.uid)
                        userPreferences.setOnboarded(true)
                    }
                }

                val target by launchTarget.collectAsState()

                LaunchedEffect(target) {
                    when (val t = target) {
                        null -> Unit

                        is LaunchTarget.OpenGroup -> {
                            // Only meaningful once signed in. Mid onboarding we
                            // drop it rather than pushing a screen that cannot load.
                            if (firebaseAuth.currentUser != null) {
                                navController.navigate(Screen.Group.createRoute(t.groupId)) {
                                    launchSingleTop = true
                                }
                            }
                            launchTarget.value = null
                        }

                        is LaunchTarget.Invite -> {
                            // Persisted rather than acted on here, so the invite
                            // survives onboarding. CreateOrJoinViewModel picks it
                            // up when Home appears, which is the first moment a
                            // join can actually succeed.
                            userPreferences.savePendingInviteCode(t.code)

                            if (firebaseAuth.currentUser != null) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            launchTarget.value = null
                        }
                    }
                }

                PamojaNavGraph(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }

    /** Fired when the app is already running and a link or notification arrives. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // Notification tap. This is also the only reliable signal that our
        // notifications are actually landing, so it resets the backoff counter
        // that would otherwise eventually silence us.
        val groupId = intent.getStringExtra(SmartNotificationHelper.EXTRA_GROUP_ID)
        if (!groupId.isNullOrBlank()) {
            lifecycleScope.launch { userPreferences.resetIgnoredNotifications() }
            launchTarget.value = LaunchTarget.OpenGroup(groupId)
            // Cleared so a configuration change does not replay the navigation.
            intent.removeExtra(SmartNotificationHelper.EXTRA_GROUP_ID)
            return
        }

        // Verified https App Link, or the legacy pamoja:// scheme.
        val code = InviteLink.parseCode(intent.data?.toString())
        if (!code.isNullOrBlank()) {
            launchTarget.value = LaunchTarget.Invite(code)
            intent.data = null
        }
    }
}
