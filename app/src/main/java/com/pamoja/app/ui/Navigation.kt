package com.pamoja.app.ui

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object SignIn : Screen("sign_in")
    object ProfileSetup : Screen("profile_setup")
    object HealthConnect : Screen("health_connect")
    object Home : Screen("home")
    object CreateGroup : Screen("create_group")
    object Invite : Screen("invite/{groupId}") {
        fun createRoute(groupId: String) = "invite/$groupId"
    }
    object Group : Screen("group/{groupId}") {
        fun createRoute(groupId: String) = "group/$groupId"
    }
    object Settings : Screen("settings")
}