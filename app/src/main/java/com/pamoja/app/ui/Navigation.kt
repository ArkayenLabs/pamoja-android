package com.pamoja.app.ui

import android.net.Uri
import com.pamoja.app.domain.model.PamojaGroupId

sealed class Screen(val route: String) {
    /** The Start screen (the route keeps its old name). */
    object IntroOne : Screen("intro/one")
    /** Paste an invite link before signing in. */
    object EnterInvite : Screen("enter_invite")

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
    object EmailSignIn : Screen("email_sign_in")
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
    /**
     * The group home. Optional [groupId] opens that group, e.g. after joining
     * it or from a notification; without it Home shows the group chosen last.
     */
    object Home : Screen("home?groupId={groupId}") {
        fun createRoute(groupId: String? = null) =
            if (groupId == null) "home" else "home?groupId=$groupId"
    }
    object CreateGroup : Screen("create_group")
    /**
     * Share a group's invite. [created] marks the step right after creating
     * the group, where step access comes next; from Home it just goes back.
     */
    object Invite : Screen("invite/{groupId}?created={created}") {
        fun createRoute(groupId: String, created: Boolean = false) =
            if (created) "invite/$groupId?created=true" else "invite/$groupId"
    }
    /** Members, settings, Premium and leaving, from Home. */
    object GroupDetails : Screen("group/{groupId}/details") {
        fun createRoute(groupId: String) = "group/$groupId/details"
    }
    /** This week as a shareable picture, from group details. */
    object WeeklyCard : Screen("group/{groupId}/card") {
        fun createRoute(groupId: String) = "group/$groupId/card"
    }
    /** Completed weeks for one group. The current week is on Home. */
    object WeeklyReview : Screen("group/{groupId}/review") {
        fun createRoute(groupId: String) = "group/$groupId/review"
    }

    /** Invite preview. Resolves a code and asks before joining anything. */
    object JoinPreview : Screen("join/{code}?auto={auto}") {
        /** [auto]: Join group was already tapped before sign-in, so join on arrival. */
        fun createRoute(code: String, auto: Boolean = false) = if (auto) "join/$code?auto=true" else "join/$code"
    }
    /** The invite before sign-in: what the group is, then sign in to join it. */
    object Invited : Screen("invited/{code}") {
        fun createRoute(code: String) = "invited/$code"
    }
    /** Paste or scan an invite while signed in, from the group switcher. */
    object JoinWithLink : Screen("join_with_link")
    object Settings : Screen("settings")

    /** Editing the profile captured during onboarding, reachable from Settings. */
    object EditProfile : Screen("edit_profile")

    /** Connected sign-in methods, adding another, and changing the password. */
    object Account : Screen("account")

    /** Open source licences, generated at build time. Legally required. */
    object Licenses : Screen("licenses")

    /** Past updates, from Settings, after the system tray forgets them. */
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
