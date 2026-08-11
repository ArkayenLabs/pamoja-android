package com.pamoja.app.ui

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")

    /**
     * Wrapper around the sign-in screens. They share one AuthViewModel scoped to
     * this graph, so the phone number survives the hop to the OTP screen without
     * being passed through the route.
     */
    object AuthGraph : Screen("auth_graph")
    object AuthLanding : Screen("auth_landing")
    object PhoneEntry : Screen("phone_entry")
    object Otp : Screen("otp")
    object EmailAuth : Screen("email_auth")
    object ForgotPassword : Screen("forgot_password?email={email}") {
        fun createRoute(email: String) = "forgot_password?email=$email"
    }

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

    /** Invite preview. Resolves a code and asks before joining anything. */
    object JoinPreview : Screen("join/{code}") {
        fun createRoute(code: String) = "join/$code"
    }
    object Settings : Screen("settings")
}
