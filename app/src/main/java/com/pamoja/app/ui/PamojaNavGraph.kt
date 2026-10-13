package com.pamoja.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.pamoja.app.ui.auth.AuthDestination
import com.pamoja.app.ui.auth.AuthLandingScreen
import com.pamoja.app.ui.auth.AuthViewModel
import com.pamoja.app.ui.auth.EmailAuthScreen
import com.pamoja.app.ui.auth.EmailSignInScreen
import com.pamoja.app.ui.auth.ForgotPasswordScreen
import com.pamoja.app.ui.auth.OtpScreen
import com.pamoja.app.ui.auth.PhoneEntryScreen
import com.pamoja.app.ui.group.CreateGroupScreen
import com.pamoja.app.ui.group.GroupDetailsScreen
import com.pamoja.app.ui.join.JoinGroupScreen
import com.pamoja.app.ui.join.InvitedScreen
import com.pamoja.app.ui.join.JoinWithLinkScreen
import com.pamoja.app.ui.weeklyreview.WeeklyReviewScreen
import com.pamoja.app.ui.home.HomeScreen
import com.pamoja.app.ui.invite.InviteScreen
import com.pamoja.app.ui.onboarding.EnterInviteScreen
import com.pamoja.app.ui.onboarding.HealthConnectScreen
import com.pamoja.app.ui.onboarding.ProfileSetupScreen
import com.pamoja.app.ui.onboarding.StartScreen

/**
 * Set by the profile editor on the entry beneath it when a save succeeds, so
 * Settings can acknowledge a save that happened on a screen which has since
 * been popped.
 */
private const val KEY_PROFILE_SAVED = "profile_saved"

/**
 * Set on Home's entry by group details when this person left or deleted the
 * group, so Home moves to their next group straight away.
 */
private const val KEY_GONE_GROUP = "gone_group"

@Composable
fun PamojaNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { pamojaEnter(returning = false) },
        exitTransition = { pamojaExit(returning = false) },
        popEnterTransition = { pamojaEnter(returning = true) },
        popExitTransition = { pamojaExit(returning = true) },
    ) {

        // The Start screen asks for nothing. An invite link pasted here is kept
        // and picked up after sign-in, like a tapped one.
        composable(Screen.IntroOne.route) {
            StartScreen(
                onGetStarted = {
                    navController.navigate(Screen.AuthGraph.route) { launchSingleTop = true }
                },
                onHaveInvite = {
                    navController.navigate(Screen.EnterInvite.route) { launchSingleTop = true }
                },
            )
        }

        composable(Screen.EnterInvite.route) {
            EnterInviteScreen(
                onContinue = { code ->
                    navController.navigate(Screen.Invited.createRoute(code)) {
                        popUpTo(Screen.EnterInvite.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // The invite before sign-in. "Join group" goes on to sign-in with the
        // decision kept; "Not now" goes back, the invite still waiting.
        composable(
            route = Screen.Invited.route,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { entry ->
            val code = entry.arguments?.getString("code").orEmpty()
            val leave = {
                if (!navController.popBackStack()) {
                    navController.navigate(Screen.AuthGraph.route) {
                        popUpTo(Screen.Invited.route) { inclusive = true }
                    }
                }
            }
            InvitedScreen(
                code = code,
                onJoin = {
                    navController.navigate(Screen.AuthGraph.route) {
                        popUpTo(Screen.Invited.route) { inclusive = true }
                    }
                },
                onNotNow = { leave() },
                onSignIn = {
                    navController.navigate(Screen.AuthGraph.route) {
                        popUpTo(Screen.Invited.route) { inclusive = true }
                    }
                },
            )
        }

        // ─── Authentication ─────────────────────────────────────────────────────
        // Nested so all four screens share one AuthViewModel. The phone number
        // and verification ID live in that shared state rather than being
        // threaded through routes.

        navigation(
            route = Screen.AuthGraph.route,
            startDestination = Screen.AuthLanding.route,
        ) {
            composable(Screen.AuthLanding.route) { entry ->
                val viewModel = authViewModel(entry, navController)
                AuthRouting(viewModel, navController)
                val cameFromStart = navController.previousBackStackEntry?.destination?.route in
                    setOf(Screen.IntroOne.route, Screen.EnterInvite.route)
                AuthLandingScreen(
                    viewModel = viewModel,
                    onChoosePhone = {
                        navController.navigate(Screen.PhoneEntry.route) { launchSingleTop = true }
                    },
                    onChooseEmail = {
                        navController.navigate(Screen.EmailSignIn.route) { launchSingleTop = true }
                    },
                    onBack = if (cameFromStart) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                )
            }

            composable(Screen.EmailSignIn.route) { entry ->
                val viewModel = authViewModel(entry, navController)
                AuthRouting(viewModel, navController)
                EmailSignInScreen(
                    viewModel = viewModel,
                    onCreateAccount = {
                        navController.navigate(Screen.EmailAuth.route) { launchSingleTop = true }
                    },
                    onForgotPassword = { email ->
                        navController.navigate(Screen.ForgotPassword.createRoute(email)) {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.PhoneEntry.route) { entry ->
                val viewModel = authViewModel(entry, navController)
                AuthRouting(viewModel, navController)
                PhoneEntryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onCodeSent = { navController.navigate(Screen.Otp.route) },
                )
            }

            composable(Screen.Otp.route) { entry ->
                val viewModel = authViewModel(entry, navController)
                AuthRouting(viewModel, navController)
                OtpScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.EmailAuth.route) { entry ->
                val viewModel = authViewModel(entry, navController)
                AuthRouting(viewModel, navController)
                EmailAuthScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.ForgotPassword.route,
                arguments = listOf(
                    navArgument("email") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
            ) { entry ->
                val viewModel = authViewModel(entry, navController)
                ForgotPasswordScreen(
                    viewModel = viewModel,
                    prefilledEmail = entry.arguments?.getString("email").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(
                onContinue = { hasPendingInvite ->
                    navController.navigate(Screen.Home.createRoute()) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                    // Without an invite the next step is starting a group, on top
                    // of Home so Back from the new group lands somewhere real.
                    // With one, Home itself opens the invite preview.
                    if (!hasPendingInvite) {
                        navController.navigate(Screen.CreateGroup.route)
                    }
                }
            )
        }

        composable(
            route = Screen.HealthConnect.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val leaveHealthConnect: () -> Unit = { navController.openGroupHome(groupId) }

            HealthConnectScreen(
                onConnected = leaveHealthConnect,
                onSkip = leaveHealthConnect,
            )
        }

        // ─── Main App ────────────────────────────────────────────────────────────
        // One home: your group. Everything else is reached from it; no tabs.

        composable(
            route = Screen.Home.route,
            arguments = listOf(
                navArgument("groupId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { entry ->
            val savedHandle = entry.savedStateHandle
            val goneGroupId by savedHandle
                .getStateFlow<String?>(KEY_GONE_GROUP, null)
                .collectAsStateWithLifecycle()

            HomeScreen(
                goneGroupId = goneGroupId,
                onGoneGroupHandled = { savedHandle[KEY_GONE_GROUP] = null },
                onOpenDetails = { groupId ->
                    navController.navigate(Screen.GroupDetails.createRoute(groupId)) { launchSingleTop = true }
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                },
                onInvite = { groupId -> navController.navigate(Screen.Invite.createRoute(groupId)) },
                onOpenWeeklyCard = { groupId -> navController.navigate(Screen.WeeklyCard.createRoute(groupId)) },
                onOpenWeeklyReview = { groupId -> navController.navigate(Screen.WeeklyReview.createRoute(groupId)) },
                onStartGroup = {
                    navController.navigate(Screen.CreateGroup.route) { launchSingleTop = true }
                },
                onJoinWithLink = {
                    navController.navigate(Screen.JoinWithLink.route) { launchSingleTop = true }
                },
                onOpenInvite = { code, auto ->
                    navController.navigate(Screen.JoinPreview.createRoute(code, auto)) { launchSingleTop = true }
                },
                onOpenPaywall = { groupId -> navController.navigate(Screen.Paywall.createRoute(groupId)) },
                onSessionExpired = {
                    navController.navigate(Screen.AuthGraph.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.GroupDetails.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            GroupDetailsScreen(
                onBack = { navController.popBackStack() },
                onInvite = { id -> navController.navigate(Screen.Invite.createRoute(id)) },
                onOpenWeeklyCard = { navController.navigate(Screen.WeeklyCard.createRoute(groupId)) },
                onOpenReview = { navController.navigate(Screen.WeeklyReview.createRoute(groupId)) },
                onOpenPaywall = { navController.navigate(Screen.Paywall.createRoute(groupId)) },
                // Tells Home outright rather than waiting for a listener to
                // notice, so it moves to another group the moment it shows.
                onGone = {
                    val home = runCatching { navController.getBackStackEntry(Screen.Home.route) }.getOrNull()
                    if (home != null) {
                        home.savedStateHandle[KEY_GONE_GROUP] = groupId
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    } else {
                        navController.navigate(Screen.Home.createRoute()) {
                            popUpTo(Screen.GroupDetails.route) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(
            route = Screen.WeeklyCard.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            com.pamoja.app.ui.card.WeeklyCardScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.JoinWithLink.route) {
            JoinWithLinkScreen(
                onOpenInvite = { code ->
                    navController.navigate(Screen.JoinPreview.createRoute(code)) {
                        popUpTo(Screen.JoinWithLink.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Activity.route) {
            com.pamoja.app.ui.activity.ActivityScreen(
                onBack = { navController.popBackStack() },
                onOpenGroup = { groupId -> navController.openGroupHome(groupId) }
            )
        }

        composable(Screen.Settings.route) { entry ->
            val savedHandle = entry.savedStateHandle
            val profileWasSaved by savedHandle
                .getStateFlow(KEY_PROFILE_SAVED, false)
                .collectAsStateWithLifecycle()

            com.pamoja.app.ui.settings.SettingsScreen(
                profileWasSaved = profileWasSaved,
                onProfileSavedShown = { savedHandle[KEY_PROFILE_SAVED] = false },
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Screen.AuthGraph.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onEditProfile = {
                    navController.navigate(Screen.EditProfile.route)
                },
                onNotificationSettings = {
                    navController.navigate(Screen.NotificationSettings.route)
                },
                onActivity = {
                    navController.navigate(Screen.Activity.route)
                },
                onAccount = {
                    navController.navigate(Screen.Account.route)
                },
                onLicenses = {
                    navController.navigate(Screen.Licenses.route)
                }
            )
        }

        composable(Screen.Licenses.route) {
            com.pamoja.app.ui.licenses.LicensesScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // From group details and the weekly review; see Screen.Paywall.
        composable(
            route = Screen.Paywall.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            com.pamoja.app.ui.paywall.PaywallScreen(
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Account.route) {
            com.pamoja.app.ui.account.AccountScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.EditProfile.route) {
            com.pamoja.app.ui.profile.EditProfileScreen(
                onBack = {
                    navController.popBackStack()
                },
                // Flagged on the entry we are returning to, not on this one,
                // which is about to be destroyed.
                onSaved = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_PROFILE_SAVED, true)
                    navController.popBackStack()
                },
            )
        }

        composable(Screen.NotificationSettings.route) {
            com.pamoja.app.ui.notifications.NotificationSettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.CreateGroup.route) {
            CreateGroupScreen(
                onGroupCreated = { groupId ->
                    navController.navigate(Screen.Invite.createRoute(groupId, created = true)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Invite.route,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("created") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val created = backStackEntry.arguments?.getBoolean("created") ?: false
            InviteScreen(
                groupId = groupId,
                onGoToGroup = {
                    if (created) {
                        // A new group: step access comes next, then its home.
                        navController.navigate(Screen.HealthConnect.createRoute(groupId)) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.WeeklyReview.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            WeeklyReviewScreen(
                onBack = { if (!navController.popBackStack()) navController.openGroupHome(groupId) },
                onOpenPremium = { navController.navigate(Screen.Paywall.createRoute(groupId)) },
            )
        }

        composable(
            route = Screen.JoinPreview.route,
            arguments = listOf(
                navArgument("code") { type = NavType.StringType },
                navArgument("auto") { type = NavType.BoolType; defaultValue = false },
            )
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code").orEmpty()
            JoinGroupScreen(
                code = code,
                autoJoin = backStackEntry.arguments?.getBoolean("auto") == true,
                onJoined = { groupId ->
                    // Connection is requested only now, when a real group gives
                    // the permission a clear purpose. The preview is removed so
                    // Back cannot return to an accepted invite.
                    navController.navigate(Screen.HealthConnect.createRoute(groupId)) {
                        popUpTo(Screen.JoinPreview.route) { inclusive = true }
                    }
                },
                onCancel = {
                    if (!navController.popBackStack()) {
                        // Arrived straight from a link, so there is no back stack
                        // to return to.
                        navController.navigate(Screen.Home.createRoute()) {
                            popUpTo(Screen.JoinPreview.route) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}

/**
 * Home showing [groupId], e.g. after joining it or from a notification.
 * Replaces the Home entry rather than stacking a second one, so Back never
 * lands on another copy of Home.
 */
internal fun NavHostController.openGroupHome(groupId: String) {
    navigate(Screen.Home.createRoute(groupId)) {
        popUpTo(Screen.Home.route) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * Navigation uses one quiet spatial rule: moving deeper comes from the right,
 * while Back returns toward the right. It stays below one perceptual beat so a
 * destination feels connected to the tap rather than waiting for an effect.
 */
private fun pamojaEnter(returning: Boolean): EnterTransition {
    val direction = if (returning) -1 else 1
    return slideInHorizontally(
        animationSpec = tween(NAV_DURATION_MS),
        initialOffsetX = { fullWidth -> direction * (fullWidth / 20) },
    ) + fadeIn(animationSpec = tween(NAV_FADE_MS))
}

private fun pamojaExit(returning: Boolean): ExitTransition {
    val direction = if (returning) -1 else 1
    return slideOutHorizontally(
        animationSpec = tween(NAV_DURATION_MS),
        targetOffsetX = { fullWidth -> -direction * (fullWidth / 28) },
    ) + fadeOut(animationSpec = tween(NAV_FADE_MS))
}

/**
 * Resolves the AuthViewModel against the auth graph's back stack entry rather
 * than the individual screen's, which is what makes all four screens share one
 * instance and therefore one piece of state.
 */
@Composable
private fun authViewModel(
    entry: NavBackStackEntry,
    navController: NavHostController,
): AuthViewModel {
    val parentEntry = remember(entry) {
        navController.getBackStackEntry(Screen.AuthGraph.route)
    }
    return hiltViewModel(parentEntry)
}

/**
 * Sends the user onward once any method succeeds.
 *
 * A brand new account needs a profile; an account that already has one is
 * returning, most likely on a replacement phone, and putting it back through
 * profile setup would be nonsense. The whole auth graph is popped either way so
 * the back gesture cannot return to a sign-in screen after signing in.
 */
@Composable
private fun AuthRouting(
    viewModel: AuthViewModel,
    navController: NavHostController,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            AuthDestination.ProfileSetup -> {
                navController.navigate(Screen.ProfileSetup.route) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
                viewModel.clearDestination()
            }

            AuthDestination.Home -> {
                navController.navigate(Screen.Home.createRoute()) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
                viewModel.clearDestination()
            }

            null -> Unit
        }
    }
}

/** Screen changes: a short slide, and a quicker fade so the old screen is gone first. */
private const val NAV_DURATION_MS = 160
private const val NAV_FADE_MS = 100
