package com.pamoja.app.ui

import android.net.Uri
import com.pamoja.app.domain.model.PamojaGroupId

sealed class Screen(val route: String) {
    object IntroOne : Screen("intro/one")
    object IntroTwo : Screen("intro/two")

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
        fun createRoute(email: String) = "forgot_password?email=${Uri.encode(email)}"
    }

    object ProfileSetup : Screen("profile_setup")
    object HealthConnect : Screen("connect_steps/{groupId}") {
        fun createRoute(groupId: String): String {
            require(PamojaGroupId.isValid(groupId)) { "A valid group is required" }
            return "connect_steps/$groupId"
        }
    }
    object Home : Screen("home")
    object Groups : Screen("groups")
    object CreateGroup : Screen("create_group")
    object Invite : Screen("invite/{groupId}") {
        fun createRoute(groupId: String) = "invite/$groupId"
    }
    object Group : Screen("group/{groupId}") {
        fun createRoute(groupId: String) = "group/$groupId"
    }
    /** Completed weeks for one group. Current week progress remains on Group. */
    object WeeklyReview : Screen("group/{groupId}/review") {
        fun createRoute(groupId: String) = "group/$groupId/review"
    }
    object NextWeekPlan : Screen("group/{groupId}/plan") {
        fun createRoute(groupId: String) = "group/$groupId/plan"
    }
    object Adventure : Screen("group/{groupId}/adventure") {
        fun createRoute(groupId: String): String {
            require(PamojaGroupId.isValid(groupId))
            return "group/$groupId/adventure"
        }
    }
    /** Admin-only settings for an existing group. */
    object EditGroup : Screen("group/{groupId}/edit") {
        fun createRoute(groupId: String) = "group/$groupId/edit"
    }

    /** Invite preview. Resolves a code and asks before joining anything. */
    object JoinPreview : Screen("join/{code}") {
        fun createRoute(code: String) = "join/$code"
    }
    object Settings : Screen("settings")

    /** Editing the profile captured during onboarding, reachable from Settings. */
    object EditProfile : Screen("edit_profile")

    /** Connected sign-in methods, adding another, and changing the password. */
    object Account : Screen("account")

    /** Open source licences, generated at build time. Legally required. */
    object Licenses : Screen("licenses")

    /** Past updates, opened from the dashboard bell after the system tray forgets them. */
    object Activity : Screen("activity")

    /**
     * Upgrade one current group to Premium. Only its ID travels through
     * navigation; the paywall reloads the name and membership before showing
     * products. Its group-screen entry remains dormant unless the separate
     * subscription sales switch is enabled.
     */
    object Paywall : Screen("group/{groupId}/sponsor") {
        fun createRoute(groupId: String): String {
            require(PamojaGroupId.isValid(groupId)) { "A valid group is required" }
            return "group/$groupId/sponsor"
        }
    }

    /** Per-channel notification control and quiet hours. */
    object NotificationSettings : Screen("notification_settings")
}
