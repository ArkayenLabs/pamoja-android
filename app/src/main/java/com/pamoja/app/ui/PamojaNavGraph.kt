package com.pamoja.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pamoja.app.ui.group.CreateGroupScreen
import com.pamoja.app.ui.group.GroupScreen
import com.pamoja.app.ui.home.HomeScreen
import com.pamoja.app.ui.invite.InviteScreen
import com.pamoja.app.ui.onboarding.HealthConnectScreen
import com.pamoja.app.ui.onboarding.ProfileSetupScreen
import com.pamoja.app.ui.onboarding.SignInScreen
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
                onGetStarted = {
                    navController.navigate(Screen.ProfileSetup.route)
                },
                onSignIn = {
                    navController.navigate(Screen.SignIn.route)
                }
            )
        }

        composable(Screen.SignIn.route) {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
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
                onSessionExpired = {
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
            GroupScreen(groupId = groupId)
        }
    }
}