package com.pamoja.app.ui

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object ProfileSetup : Screen("profile_setup")
    object HealthConnect : Screen("health_connect")
    object CreateOrJoinGroup : Screen("create_or_join_group")
    object CreateGroup : Screen("create_group")
    object Invite : Screen("invite/{groupId}") {
        fun createRoute(groupId: String) = "invite/$groupId"
    }
    object Group : Screen("group/{groupId}") {
        fun createRoute(groupId: String) = "group/$groupId"
    }
}