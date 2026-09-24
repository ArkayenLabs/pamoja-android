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
import com.pamoja.app.ui.auth.ForgotPasswordScreen
import com.pamoja.app.ui.auth.OtpScreen
import com.pamoja.app.ui.auth.PhoneEntryScreen
import com.pamoja.app.ui.group.CreateGroupScreen
import com.pamoja.app.ui.join.JoinGroupScreen
import com.pamoja.app.ui.group.GroupScreen
import com.pamoja.app.ui.weeklyreview.WeeklyReviewScreen
import com.pamoja.app.ui.home.HomeScreen
import com.pamoja.app.ui.home.GroupsScreen
import com.pamoja.app.ui.invite.InviteScreen
import com.pamoja.app.ui.onboarding.HealthConnectScreen
import com.pamoja.app.ui.onboarding.ProductIntroOneScreen
import com.pamoja.app.ui.onboarding.ProductIntroTwoScreen
import com.pamoja.app.ui.onboarding.ProfileSetupScreen
import com.pamoja.app.ui.theme.PamojaMotion

/**
 * Set by the profile editor on the entry beneath it when a save succeeds, so
 * Settings can acknowledge a save that happened on a screen which has since
 * been popped.
 */
private const val KEY_PROFILE_SAVED = "profile_saved"

@Composable
fun PamojaNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            pamojaEnter(
                from = initialState.destination.route,
                to = targetState.destination.route,
                returning = false,
            )
        },
        exitTransition = {
            pamojaExit(
                from = initialState.destination.route,
                to = targetState.destination.route,
                returning = false,
            )
        },
        popEnterTransition = {
            pamojaEnter(
                from = initialState.destination.route,
                to = targetState.destination.route,
                returning = true,
            )
        },
        popExitTransition = {
            pamojaExit(
                from = initialState.destination.route,
                to = targetState.destination.route,
                returning = true,
            )
        },
    ) {

        // Product education stays separate from authentication. These screens
        // explain what Pamoja does and ask for no identity or permission.
        composable(Screen.IntroOne.route) {
            ProductIntroOneScreen(
                onContinue = { navController.navigate(Screen.IntroTwo.route) },
            )
        }

        composable(Screen.IntroTwo.route) {
            ProductIntroTwoScreen(
                onBack = { navController.popBackStack() },
                onContinue = {
                    navController.navigate(Screen.AuthGraph.route) {
                        launchSingleTop = true
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
                AuthLandingScreen(
                    viewModel = viewModel,
                    onChoosePhone = {
                        navController.navigate(Screen.PhoneEntry.route) {
                            launchSingleTop = true
                        }
                    },
                    onCreateAccount = {
                        navController.navigate(Screen.EmailAuth.route) {
                            launchSingleTop = true
                        }
                    },
                    onForgotPassword = { email ->
                        navController.navigate(Screen.ForgotPassword.createRoute(email)) {
                            launchSingleTop = true
                        }
                    },
                    onBack = if (
                        navController.previousBackStackEntry?.destination?.route ==
                        Screen.IntroTwo.route
                    ) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
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
                onContinue = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.HealthConnect.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val leaveHealthConnect: () -> Unit = {
                navController.navigate(Screen.Group.createRoute(groupId)) {
                    popUpTo(Screen.HealthConnect.route) { inclusive = true }
                    launchSingleTop = true
                }
            }

            HealthConnectScreen(
                onConnected = leaveHealthConnect,
                onSkip = leaveHealthConnect,
            )
        }

        // ─── Main App ────────────────────────────────────────────────────────────

        composable(Screen.Home.route) {
            HomeScreen(
                onGroupClick = { groupId ->
                    navController.navigate(Screen.Group.createRoute(groupId))
                },
                onGroupsClick = { navController.navigateMainTab(Screen.Groups.route) },
                onSettingsClick = { navController.navigateMainTab(Screen.Settings.route) },
                onSessionExpired = {
                    navController.navigate(Screen.AuthGraph.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onOpenInvite = { code ->
                    navController.navigate(Screen.JoinPreview.createRoute(code))
                },
                onActivityClick = { navController.navigate(Screen.Activity.route) }
            )
        }

        composable(Screen.Groups.route) {
            GroupsScreen(
                onGroupClick = { groupId ->
                    navController.navigate(Screen.Group.createRoute(groupId))
                },
                onCreateGroup = { navController.navigate(Screen.CreateGroup.route) },
                onTodayClick = { navController.navigateMainTab(Screen.Home.route) },
                onSettingsClick = { navController.navigateMainTab(Screen.Settings.route) },
                onSessionExpired = {
                    navController.navigate(Screen.AuthGraph.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onOpenInvite = { code ->
                    navController.navigate(Screen.JoinPreview.createRoute(code))
                },
            )
        }

        composable(Screen.Activity.route) {
            com.pamoja.app.ui.activity.ActivityScreen(
                onBack = { navController.popBackStack() },
                onOpenGroup = { groupId ->
                    navController.navigate(Screen.Group.createRoute(groupId))
                }
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
                onToday = { navController.navigateMainTab(Screen.Home.route) },
                onGroups = { navController.navigateMainTab(Screen.Groups.route) },
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

        // Reachable by route only. Nothing navigates here yet, by design: see
        // Screen.Paywall.
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
                    navController.navigate(Screen.Invite.createRoute(groupId)) {
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
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            InviteScreen(
                groupId = groupId,
                onGoToGroup = {
                    navController.navigate(Screen.HealthConnect.createRoute(groupId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Group.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onShareInvite = {
                    navController.navigate(Screen.Invite.createRoute(groupId))
                },
                onEditGroup = {
                    navController.navigate(Screen.EditGroup.createRoute(groupId))
                },
                onOpenWeeklyReview = {
                    navController.navigate(Screen.WeeklyReview.createRoute(groupId))
                },
                onOpenNextWeekPlan = {
                    navController.navigate(Screen.NextWeekPlan.createRoute(groupId))
                },
                onOpenAdventure = { navController.navigate(Screen.Adventure.createRoute(groupId)) },
                onSponsorGroup = {
                    navController.navigate(Screen.Paywall.createRoute(groupId))
                },
            )
        }

        composable(Screen.Adventure.route, arguments = listOf(navArgument("groupId") { type = NavType.StringType })) { entry ->
            val groupId = entry.arguments?.getString("groupId").orEmpty()
            com.pamoja.app.ui.adventure.AdventureScreen(
                onBack = { navController.popBackStack() },
                onPremium = { navController.navigate(Screen.Paywall.createRoute(groupId)) },
                onConnect = { navController.navigate(Screen.HealthConnect.createRoute(groupId)) },
            )
        }

        composable(
            route = Screen.NextWeekPlan.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            WeeklyReviewScreen(
                onBack = { navController.popBackStack() },
                onContinue = { navController.popBackStack() },
                onOpenPremium = { navController.navigate(Screen.Paywall.createRoute(groupId)) },
                planningOnly = true,
            )
        }

        composable(
            route = Screen.WeeklyReview.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            WeeklyReviewScreen(
                onBack = { navController.popBackStack() },
                onOpenPremium = {
                    navController.navigate(Screen.Paywall.createRoute(groupId))
                },
                onContinue = {
                    val groupRoute = Screen.Group.createRoute(groupId)
                    if (!navController.popBackStack(Screen.Group.route, inclusive = false)) {
                        navController.navigate(groupRoute) { launchSingleTop = true }
                    }
                },
            )
        }

        composable(
            route = Screen.EditGroup.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            com.pamoja.app.ui.group.EditGroupScreen(
                onBack = { navController.popBackStack() },
                // Replaces the group entry rather than popping back to it.
                //
                // GroupViewModel reads the group document once, in loadGroup,
                // and survives a plain popBackStack, so returning that way
                // would land on a screen still showing the old name and the
                // old goal, which reads as the save having failed. Popping the
                // entry and pushing a fresh one forces the reload.
                onSaved = {
                    navController.navigate(Screen.Group.createRoute(groupId)) {
                        popUpTo(Screen.Group.route) { inclusive = true }
                    }
                },
                onDeleted = {
                    navController.navigate(Screen.Groups.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = Screen.JoinPreview.route,
            arguments = listOf(navArgument("code") { type = NavType.StringType })
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code").orEmpty()
            JoinGroupScreen(
                code = code,
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
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.JoinPreview.route) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}

/** Keeps the three main destinations as peers and restores their scroll state. */
private fun NavHostController.navigateMainTab(route: String) {
    navigate(route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Navigation uses one quiet spatial rule: moving deeper comes from the right,
 * while Back returns toward the right. It stays below one perceptual beat so a
 * destination feels connected to the tap rather than waiting for an effect.
 */
private fun pamojaEnter(from: String?, to: String?, returning: Boolean): EnterTransition {
    val tabDirection = mainTabIndex(from)?.let { fromIndex ->
        mainTabIndex(to)?.let { toIndex -> if (toIndex >= fromIndex) 1 else -1 }
    }
    val direction = tabDirection ?: if (returning) -1 else 1
    return slideInHorizontally(
        animationSpec = tween(PamojaMotion.durationFast),
        initialOffsetX = { fullWidth -> direction * (fullWidth / 20) },
    ) + fadeIn(animationSpec = tween(PamojaMotion.durationInstant))
}

private fun pamojaExit(from: String?, to: String?, returning: Boolean): ExitTransition {
    val tabDirection = mainTabIndex(from)?.let { fromIndex ->
        mainTabIndex(to)?.let { toIndex -> if (toIndex >= fromIndex) 1 else -1 }
    }
    val direction = tabDirection ?: if (returning) -1 else 1
    return slideOutHorizontally(
        animationSpec = tween(PamojaMotion.durationFast),
        targetOffsetX = { fullWidth -> -direction * (fullWidth / 28) },
    ) + fadeOut(animationSpec = tween(PamojaMotion.durationInstant))
}

private fun mainTabIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Groups.route -> 1
    Screen.Settings.route -> 2
    else -> null
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
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
                viewModel.clearDestination()
            }

            null -> Unit
        }
    }
}
