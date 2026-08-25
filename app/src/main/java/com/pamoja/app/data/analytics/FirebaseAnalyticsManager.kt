package com.pamoja.app.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.pamoja.app.domain.analytics.AnalyticsEvent
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.analytics.AnalyticsParam
import javax.inject.Inject

/**
 * Firebase-backed implementation of [AnalyticsManager].
 *
 * Each convenience method builds a [Bundle] from the domain-layer constants
 * and delegates to [FirebaseAnalytics.logEvent].
 */
class FirebaseAnalyticsManager @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) : AnalyticsManager {

    override fun logEvent(eventName: String, params: Map<String, Any>?) {
        val bundle = params?.toBundle()
        firebaseAnalytics.logEvent(eventName, bundle)
    }

    // ── Onboarding funnel ────────────────────────────────────────────────────────

    override fun logWelcomeScreenViewed() {
        logEvent(AnalyticsEvent.WELCOME_SCREEN_VIEWED, mapOf(
            AnalyticsParam.TIMESTAMP to System.currentTimeMillis()
        ))
    }

    override fun logProfileSetupStarted() {
        logEvent(AnalyticsEvent.PROFILE_SETUP_STARTED, mapOf(
            AnalyticsParam.TIMESTAMP to System.currentTimeMillis()
        ))
    }

    override fun logProfileCompleted(userId: String) {
        logEvent(AnalyticsEvent.PROFILE_COMPLETED, mapOf(
            AnalyticsParam.USER_ID to userId,
            AnalyticsParam.TIMESTAMP to System.currentTimeMillis()
        ))
    }

    override fun logHealthConnectScreenViewed() {
        logEvent(AnalyticsEvent.HEALTH_CONNECT_SCREEN_VIEWED, mapOf(
            AnalyticsParam.TIMESTAMP to System.currentTimeMillis()
        ))
    }

    override fun logHealthConnectPermissionRequested() {
        logEvent(AnalyticsEvent.HEALTH_CONNECT_PERMISSION_REQUESTED, mapOf(
            AnalyticsParam.TIMESTAMP to System.currentTimeMillis()
        ))
    }

    override fun logHealthConnectPermissionGranted(userId: String) {
        logEvent(AnalyticsEvent.HEALTH_CONNECT_PERMISSION_GRANTED, mapOf(
            AnalyticsParam.USER_ID to userId,
            AnalyticsParam.TIMESTAMP to System.currentTimeMillis()
        ))
    }

    override fun logHealthConnectPermissionDenied() {
        logEvent(AnalyticsEvent.HEALTH_CONNECT_PERMISSION_DENIED, mapOf(
            AnalyticsParam.TIMESTAMP to System.currentTimeMillis()
        ))
    }

    override fun logHealthConnectSkipped() {
        logEvent(AnalyticsEvent.HEALTH_CONNECT_SKIPPED, mapOf(
            AnalyticsParam.TIMESTAMP to System.currentTimeMillis()
        ))
    }

    override fun logHomeScreenReached() {
        logEvent(AnalyticsEvent.HOME_SCREEN_REACHED, mapOf(
            AnalyticsParam.TIMESTAMP to System.currentTimeMillis()
        ))
    }

    // ── Groups ──────────────────────────────────────────────────────────────────

    override fun logGroupCreated(groupId: String, groupName: String) {
        logEvent(AnalyticsEvent.GROUP_CREATED, mapOf(
            AnalyticsParam.GROUP_ID to groupId,
            AnalyticsParam.GROUP_NAME to groupName
        ))
    }

    override fun logGroupJoined(groupId: String, userId: String) {
        logEvent(AnalyticsEvent.GROUP_JOINED, mapOf(
            AnalyticsParam.GROUP_ID to groupId,
            AnalyticsParam.USER_ID to userId
        ))
    }

    override fun logGroupScreenViewed(groupId: String) {
        logEvent(AnalyticsEvent.GROUP_SCREEN_VIEWED, mapOf(
            AnalyticsParam.GROUP_ID to groupId
        ))
    }

    override fun logWeeklyGoalReached(groupId: String, target: Int) {
        logEvent(AnalyticsEvent.WEEKLY_GOAL_REACHED, mapOf(
            AnalyticsParam.GROUP_ID to groupId,
            AnalyticsParam.TARGET to target
        ))
    }

    // ── Invites ─────────────────────────────────────────────────────────────────

    override fun logInviteScreenViewed(groupId: String) {
        logEvent(AnalyticsEvent.INVITE_SCREEN_VIEWED, mapOf(
            AnalyticsParam.GROUP_ID to groupId
        ))
    }

    override fun logInviteLinkUsed(groupId: String, userId: String) {
        logEvent(AnalyticsEvent.INVITE_LINK_USED, mapOf(
            AnalyticsParam.GROUP_ID to groupId,
            AnalyticsParam.USER_ID to userId
        ))
    }

    // ── Step Sync ───────────────────────────────────────────────────────────────

    override fun logStepsSyncStarted(userId: String) {
        logEvent(AnalyticsEvent.STEPS_SYNC_STARTED, mapOf(
            AnalyticsParam.USER_ID to userId
        ))
    }

    override fun logStepsSyncSuccess(userId: String, durationMs: Long) {
        logEvent(AnalyticsEvent.STEPS_SYNC_SUCCESS, mapOf(
            AnalyticsParam.USER_ID to userId,
            AnalyticsParam.DURATION_MS to durationMs
        ))
    }

    override fun logStepsSyncFailed(userId: String, reason: String, durationMs: Long) {
        logEvent(AnalyticsEvent.STEPS_SYNC_FAILED, mapOf(
            AnalyticsParam.USER_ID to userId,
            AnalyticsParam.REASON to reason,
            AnalyticsParam.DURATION_MS to durationMs
        ))
    }

    override fun logStepsSyncSkipped(userId: String, durationMs: Long) {
        logEvent(AnalyticsEvent.STEPS_SYNC_SKIPPED, mapOf(
            AnalyticsParam.USER_ID to userId,
            AnalyticsParam.REASON to "hc_unavailable",
            AnalyticsParam.DURATION_MS to durationMs
        ))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Converts a [Map] to a Firebase [Bundle].
     * Supports String, Long, Int, Double, Float, and Boolean values.
     */
    private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
        forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Long -> putLong(key, value)
                is Int -> putInt(key, value)
                is Double -> putDouble(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                else -> putString(key, value.toString())
            }
        }
    }
}
