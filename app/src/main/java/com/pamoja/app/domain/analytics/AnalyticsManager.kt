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
 * - **No step count, and no other Health Connect reading, goes into an event.**
 * - **No user identifier goes into an event either.** The Firebase UID was a
 *   parameter on eight events until 2026-08-25. It is user-linkable data in a
 *   place step counts were deliberately removed from, and it was never
 *   registered as a custom dimension, so nothing ever read it. If per-user
 *   analysis is wanted later, `setUserId()` is the API for it, not a param.
 * - **Never forward an exception message.** `logStepsSyncFailed` took
 *   `e.message` raw, and Firebase messages can carry document paths. Pass a
 *   stable classifier instead, which matches the rule for AppError: match on
 *   type, never on message text.
 *   Event names, group ids and durations are fine; the measurement is not.
 *   Step data belongs in Firestore, where `firestore.rules` protects it.
 *   `logWeeklyGoalReached` and `logStepsSyncSuccess` each carried one until
 *   2026-08-25. `legal/DATA_SAFETY.md` and the Play Health apps declaration
 *   both state that Health Connect data reaches no third party, so the two
 *   parameters were removed rather than the claim softened. Do not add one back.
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
    fun logProfileCompleted()

    /** User reached the Health Connect permission screen. */
    fun logHealthConnectScreenViewed()

    /** User tapped the button to request Health Connect permission. */
    fun logHealthConnectPermissionRequested()

    /** User granted Health Connect step-read permission. */
    fun logHealthConnectPermissionGranted()

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
    fun logGroupJoined(groupId: String)

    /** User opened the group detail screen. */
    fun logGroupScreenViewed(groupId: String)

    /** Combined weekly steps crossed the group's weekly target (logged once per session). */
    fun logWeeklyGoalReached(groupId: String, target: Int)

    // ── Invites ─────────────────────────────────────────────────────────────────

    /** User viewed the invite/share screen after creating a group. */
    fun logInviteScreenViewed(groupId: String)

    /** An invite link was consumed to join a group. */
    fun logInviteLinkUsed(groupId: String)

    // ── Step Sync ───────────────────────────────────────────────────────────────

    /** StepSyncWorker started a sync cycle. */
    fun logStepsSyncStarted()

    /** StepSyncWorker successfully wrote steps to Firestore. */
    fun logStepsSyncSuccess(durationMs: Long)

    /** StepSyncWorker encountered an error. */
    fun logStepsSyncFailed(reason: String, durationMs: Long)

    /** StepSyncWorker skipped because Health Connect returned null (unavailable/permission revoked). */
    fun logStepsSyncSkipped(durationMs: Long)
}
