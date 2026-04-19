package com.pamoja.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pamoja.app.ui.group.CreateGroupScreen
import com.pamoja.app.ui.group.GroupScreen
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
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(Screen.ProfileSetup.route)
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
                    navController.navigate(Screen.CreateOrJoinGroup.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.CreateOrJoinGroup.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.CreateOrJoinGroup.route) {
            CreateOrJoinGroupScreen(
                onCreateGroup = {
                    navController.navigate(Screen.CreateGroup.route)
                },
                onJoinGroup = { groupId ->
                    navController.navigate(Screen.Group.createRoute(groupId))
                }
            )
        }

        composable(Screen.CreateGroup.route) {
            CreateGroupScreen(
                onGroupCreated = { groupId ->
                    navController.navigate(Screen.Invite.createRoute(groupId)) {
                        popUpTo(Screen.CreateGroup.route) { inclusive = true }
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
                        popUpTo(Screen.Invite.route) { inclusive = true }
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