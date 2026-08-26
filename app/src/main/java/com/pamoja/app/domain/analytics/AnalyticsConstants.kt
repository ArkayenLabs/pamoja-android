package com.pamoja.app.domain.analytics

/**
 * Firebase Analytics event name constants.
 *
 * Every custom event logged through [AnalyticsManager] must use a constant
 * defined here. This keeps event names consistent across the codebase and
 * makes renaming/searching trivial.
 */
object AnalyticsEvent {
    // Onboarding funnel
    const val WELCOME_SCREEN_VIEWED = "welcome_screen_viewed"
    const val PROFILE_SETUP_STARTED = "profile_setup_started"
    const val PROFILE_COMPLETED = "profile_completed"
    const val HEALTH_CONNECT_SCREEN_VIEWED = "health_connect_screen_viewed"
    const val HEALTH_CONNECT_PERMISSION_REQUESTED = "health_connect_permission_requested"
    const val HEALTH_CONNECT_PERMISSION_GRANTED = "health_connect_permission_granted"
    const val HEALTH_CONNECT_PERMISSION_DENIED = "health_connect_permission_denied"
    const val HEALTH_CONNECT_SKIPPED = "health_connect_skipped"
    const val HOME_SCREEN_REACHED = "home_screen_reached"

    // Groups
    const val GROUP_CREATED = "group_created"
    const val GROUP_JOINED = "group_joined"
    const val INVITE_SCREEN_VIEWED = "invite_screen_viewed"
    const val INVITE_LINK_USED = "invite_link_used"
    const val GROUP_SCREEN_VIEWED = "group_screen_viewed"
    const val WEEKLY_GOAL_REACHED = "weekly_goal_reached"

    // Step sync
    const val STEPS_SYNC_STARTED = "steps_sync_started"
    const val STEPS_SYNC_SUCCESS = "steps_sync_success"
    const val STEPS_SYNC_FAILED = "steps_sync_failed"
    const val STEPS_SYNC_SKIPPED = "steps_sync_skipped"
}

/**
 * Firebase Analytics parameter key constants.
 *
 * Used as keys in the `params` map passed to [AnalyticsManager.logEvent].
 */
object AnalyticsParam {
    const val GROUP_ID = "group_id"
    const val GROUP_NAME = "group_name"
    const val TARGET = "target"
    const val REASON = "reason"
    const val TIMESTAMP = "timestamp"
    const val DURATION_MS = "duration_ms"
}
