package com.pamoja.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.pamoja.app.ui.home.HomeScreen
import com.pamoja.app.ui.invite.InviteScreen
import com.pamoja.app.ui.onboarding.HealthConnectScreen
import com.pamoja.app.ui.onboarding.ProfileSetupScreen
import com.pamoja.app.ui.onboarding.WelcomeScreen

@Composable
fun PamojaNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // ─── Onboarding ─────────────────────────────────────────────────────────

        composable(Screen.Welcome.route) {
            WelcomeScreen(
                // Both entry points lead to the same gate. Sign-in is mandatory,
                // so there is no separate "start without an account" path.
                onGetStarted = {
                    navController.navigate(Screen.AuthGraph.route)
                },
                onSignIn = {
                    navController.navigate(Screen.AuthGraph.route)
                }
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
                    onBack = { navController.popBackStack() },
                    onChoosePhone = { navController.navigate(Screen.PhoneEntry.route) },
                    onChooseEmail = { navController.navigate(Screen.EmailAuth.route) },
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
                    onForgotPassword = { email ->
                        navController.navigate(Screen.ForgotPassword.createRoute(email))
                    },
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
                    navController.navigate(Screen.HealthConnect.route)
                }
            )
        }

        composable(Screen.HealthConnect.route) {
            HealthConnectScreen(
                onConnected = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── Main App ────────────────────────────────────────────────────────────

        composable(Screen.Home.route) {
            HomeScreen(
                onGroupClick = { groupId ->
                    navController.navigate(Screen.Group.createRoute(groupId))
                },
                onCreateGroup = {
                    navController.navigate(Screen.CreateGroup.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onSessionExpired = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onOpenInvite = { code ->
                    navController.navigate(Screen.JoinPreview.createRoute(code))
                }
            )
        }

        composable(Screen.Settings.route) {
            com.pamoja.app.ui.settings.SettingsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onSignedOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
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
                    navController.navigate(Screen.Group.createRoute(groupId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Screen.Group.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() }
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
                    // The preview is popped so the back gesture from the group
                    // does not return to an invite that has been accepted.
                    navController.navigate(Screen.Group.createRoute(groupId)) {
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
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            AuthDestination.ProfileSetup -> {
                navController.navigate(Screen.ProfileSetup.route) {
                    popUpTo(Screen.AuthGraph.route) { inclusive = true }
                }
                viewModel.clearDestination()
            }

            AuthDestination.Home -> {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Welcome.route) { inclusive = true }
                }
                viewModel.clearDestination()
            }

            null -> Unit
        }
    }
}