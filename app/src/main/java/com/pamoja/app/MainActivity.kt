package com.pamoja.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.FirebaseAuth
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.ui.PamojaNavGraph
import com.pamoja.app.ui.Screen
import com.pamoja.app.ui.theme.PamojaTheme
import com.pamoja.app.util.InviteLink
import com.pamoja.app.util.SmartNotificationHelper
import com.pamoja.app.util.WorkManagerScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
 *  - The Firebase SDK persists the auth token across app restarts and DataStore
 *    clears, so auth.currentUser is authoritative for "is this person signed in".
 *
 *  - DataStore hasSeenIntro remembers the two product-only first-run screens.
 *    It survives sign-out because returning users should go straight to auth.
 *
 *  - DataStore isOnboarded answers a different question, "have they filled in a
 *    profile". Someone can authenticate and then kill the app before naming
 *    themselves, so the Firebase session alone is not enough.
 *
 *  Decision tree at launch:
 *    1. signed out, intro unseen           -> Product intro screen 1
 *    2. signed out, intro seen             -> Authentication choices
 *    3. signed in, not onboarded           -> ProfileSetup, finish what was started
 *    4. signed in, onboarded               -> Home
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var firebaseAuth: FirebaseAuth
    @Inject lateinit var workManagerScheduler: WorkManagerScheduler

    /** Set by onCreate and onNewIntent, consumed once by the composition. */
    private val launchTarget = MutableStateFlow<LaunchTarget?>(null)

    /**
     * Flips once startDestination below has actually resolved. Read from the
     * splash's keep-on-screen condition, which is polled on the main thread on
     * every frame, same thread this is written from inside the produceState
     * block, so a plain var is safe without further synchronisation.
     *
     * Tied to real readiness rather than a fixed delay: the splash covers
     * exactly the auth check and the DataStore onboarding read this activity
     * already had to do, the same wait that used to render as a blank frame
     * before the NavHost was allowed to start.
     */
    private var isReadyToShowContent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !isReadyToShowContent }
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
            // Defaults to System for the first frame, before DataStore answers.
            // A forced-light user sees at most one dark frame at cold start,
            // which is the cheap end of the trade against blocking the launch.
            val themePreference by userPreferences.themePreference
                .collectAsState(initial = ThemePreference.System)

            PamojaTheme(themePreference = themePreference) {
                val navController = rememberNavController()

                // Null until the onboarding flag has been read. The NavHost is
                // held back for that one frame rather than started at Home and
                // then redirected, which would flash the wrong screen.
                val startDestination by produceState<String?>(initialValue = null) {
                    val user = firebaseAuth.currentUser
                    value = when {
                        user == null && !userPreferences.hasSeenIntro.first() -> {
                            Screen.IntroOne.route
                        }

                        user == null -> Screen.AuthGraph.route

                        userPreferences.isOnboarded.first() -> {
                            // Rehydrate in case DataStore was cleared but the
                            // Firebase token survived, e.g. a backup restore.
                            userPreferences.saveUserId(user.uid)
                            Screen.Home.route
                        }

                        else -> {
                            userPreferences.saveUserId(user.uid)
                            Screen.ProfileSetup.route
                        }
                    }
                    isReadyToShowContent = true
                }

                val target by launchTarget.collectAsState()

                startDestination?.let { destination ->
                    PamojaNavGraph(
                        navController = navController,
                        startDestination = destination
                    )
                }

                // A cold-start link is parsed before setContent. Do not consume
                // it until the NavHost above exists: navigating while
                // startDestination is still null races the graph setup, which
                // used to make the tap appear to fail and leave the saved invite
                // to surface only on the next manual app launch.
                LaunchedEffect(target, startDestination) {
                    if (startDestination == null) return@LaunchedEffect

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
                            val canOpenNow = firebaseAuth.currentUser != null &&
                                userPreferences.isOnboarded.first()

                            if (canOpenNow) {
                                // A live, ready session needs no deferred copy.
                                // Avoiding the write also prevents Home from
                                // observing the same code and opening a duplicate
                                // preview alongside this direct navigation.
                                userPreferences.clearPendingInviteCode()
                                navController.navigate(
                                    Screen.JoinPreview.createRoute(t.code)
                                ) {
                                    launchSingleTop = true
                                }
                            } else {
                                // Survives product intro, sign-in and profile
                                // setup. Home consumes it once the account can
                                // resolve and join a group safely.
                                userPreferences.savePendingInviteCode(t.code)
                            }
                            launchTarget.value = null
                        }
                    }
                }
            }
        }
    }

    /**
     * Steps are refreshed here rather than anywhere in the UI tree.
     *
     * Both calls used to live in GroupViewModel.loadGroup(), which meant the
     * background schedule only ever started if someone opened a group detail
     * screen, and nothing refreshed on returning to the app at all. A user who
     * signed in and stayed on Home was never synced once, so their groups sat
     * at no progress until something unrelated happened to trigger a worker.
     *
     * onResume rather than onCreate because it also covers coming back from
     * the background, which is the moment figures are actually looked at.
     * Scheduling is idempotent under KEEP, so repeating it is free and repairs
     * a schedule that was cancelled or reached a terminal state.
     */
    override fun onResume() {
        super.onResume()
        if (firebaseAuth.currentUser == null) return

        workManagerScheduler.scheduleStepSync()

        lifecycleScope.launch {
            val since = System.currentTimeMillis() - userPreferences.lastSyncTime.first()
            if (since >= WorkManagerScheduler.OPPORTUNISTIC_SYNC_MIN_GAP_MS) {
                workManagerScheduler.syncSoon()
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
