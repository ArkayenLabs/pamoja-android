package com.pamoja.app.domain.analytics

/**
 * Centralized analytics contract.
 *
 * Implemented in the data layer ([com.pamoja.app.data.analytics.FirebaseAnalyticsManager])
 * and injected via Hilt into ViewModels and Workers.
 *
 * **Rules:**
 * - Never call Firebase Analytics directly from ViewModels or Composables.
 * - Always use this interface.
 * - Analytics calls should be triggered from ViewModels, never from Composables.
 */
interface AnalyticsManager {

    /**
     * Generic event logger. Prefer the typed convenience methods below
     * for compile-time safety and discoverability.
     */
    fun logEvent(eventName: String, params: Map<String, Any>? = null)

    // ── Onboarding funnel ────────────────────────────────────────────────────────

    /** User landed on the welcome screen (top of funnel). */
    fun logWelcomeScreenViewed()

    /** User navigated to the profile setup form. */
    fun logProfileSetupStarted()

    /** User completed the profile setup form and was created in Firestore. */
    fun logProfileCompleted(userId: String)

    /** User reached the Health Connect permission screen. */
    fun logHealthConnectScreenViewed()

    /** User tapped the button to request Health Connect permission. */
    fun logHealthConnectPermissionRequested()

    /** User granted Health Connect step-read permission. */
    fun logHealthConnectPermissionGranted(userId: String)

    /** User denied Health Connect permission in the system dialog. */
    fun logHealthConnectPermissionDenied()

    /** User tapped "Skip for now" on the Health Connect screen. */
    fun logHealthConnectSkipped()

    /** User successfully reached the home screen (bottom of onboarding funnel). */
    fun logHomeScreenReached()

    // ── Groups ──────────────────────────────────────────────────────────────────

    /** A new group was created. */
    fun logGroupCreated(groupId: String, groupName: String)

    /** User successfully joined a group. */
    fun logGroupJoined(groupId: String, userId: String)

    /** User opened the group detail screen. */
    fun logGroupScreenViewed(groupId: String)

    /** Combined weekly steps crossed the group's weekly target (logged once per session). */
    fun logWeeklyGoalReached(groupId: String, totalSteps: Long, target: Int)

    // ── Invites ─────────────────────────────────────────────────────────────────

    /** User viewed the invite/share screen after creating a group. */
    fun logInviteScreenViewed(groupId: String)

    /** An invite link was consumed to join a group. */
    fun logInviteLinkUsed(groupId: String, userId: String)

    // ── Step Sync ───────────────────────────────────────────────────────────────

    /** StepSyncWorker started a sync cycle. */
    fun logStepsSyncStarted(userId: String)

    /** StepSyncWorker successfully wrote steps to Firestore. */
    fun logStepsSyncSuccess(userId: String, stepCount: Long, durationMs: Long)

    /** StepSyncWorker encountered an error. */
    fun logStepsSyncFailed(userId: String, reason: String, durationMs: Long)

    /** StepSyncWorker skipped because Health Connect returned null (unavailable/permission revoked). */
    fun logStepsSyncSkipped(userId: String, durationMs: Long)
}
