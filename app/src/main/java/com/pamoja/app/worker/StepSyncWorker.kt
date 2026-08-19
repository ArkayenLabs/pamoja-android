package com.pamoja.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.StepRepository
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetMyStepsForWeekUseCase
import com.pamoja.app.domain.usecase.PublishMyWeeklyStepsUseCase
import com.pamoja.app.domain.usecase.GetUserGroupsUseCase
import com.pamoja.app.domain.usecase.PublishGroupWeeklyTotalUseCase
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.util.SmartNotificationHelper
import com.pamoja.app.util.SmartNotificationEngine
import com.pamoja.app.util.NotificationContext
import com.pamoja.app.util.minuteOfDayToTime
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Runs periodically (when network is available and battery is not low).
 *
 * Uses Health Connect to read steps, no foreground service, no hardware
 * sensor registration. Health Connect is a system-level store managed by
 * Google, making it reliable across OEM battery optimisers.
 *
 * Execution per run:
 *  1. Verify user is logged in (fail fast if not).
 *  2. Read today's step total from Health Connect.
 *  3. Write exactly ONE StepEntry to Firestore.
 *  4. Check if smart notifications should be triggered (throttled to 12h).
 *  5. Done.
 */
@HiltWorker
class StepSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val stepRepository: StepRepository,
    private val healthConnectReader: HealthConnectReader,
    private val analyticsManager: AnalyticsManager,
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupMembershipsUseCase: GetGroupMembershipsUseCase,
    private val getMyStepsForWeekUseCase: GetMyStepsForWeekUseCase,
    private val publishMyWeeklyStepsUseCase: PublishMyWeeklyStepsUseCase,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val publishGroupWeeklyTotalUseCase: PublishGroupWeeklyTotalUseCase,
    private val smartNotificationHelper: SmartNotificationHelper,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "doWork() started | attempt=${runAttemptCount}")

        // ── Guard: user must be authenticated ───────────────────────
        val user = authRepository.getCurrentUser()
        if (user == null) {
            Log.w(TAG, "No authenticated user, failing permanently")
            return Result.failure()
        }

        analyticsManager.logStepsSyncStarted(user.userId)

        return try {
            // ── Read today's steps from Health Connect ───────────────────
            // Returns null if HC unavailable or permission revoked, silent success, no retry
            val todaySteps = healthConnectReader.readTodaySteps()
            if (todaySteps == null) {
                val durationMs = System.currentTimeMillis() - startTime
                Log.w(TAG, "Health Connect returned null (unavailable/revoked) | duration=${durationMs}ms")
                analyticsManager.logStepsSyncSkipped(user.userId, durationMs)
                return Result.success()
            }

            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            Log.d(TAG, "Read $todaySteps steps for $todayStr")

            // ── Write to Firestore (one write per worker execution) ──────
            val entry = StepEntry(
                userId    = user.userId,
                stepCount = todaySteps,
                date      = todayStr
            )
            stepRepository.saveStepEntry(entry).getOrElse { throw it }

            val durationMs = System.currentTimeMillis() - startTime
            Log.i(TAG, "Sync success | steps=$todaySteps | duration=${durationMs}ms")
            analyticsManager.logStepsSyncSuccess(user.userId, todaySteps, durationMs)

            // Read BEFORE the timestamp below overwrites it. Zero means no sync
            // has ever landed, which is the one moment worth telling someone the
            // thing is actually on. After this line that fact is unrecoverable.
            val isFirstEverSync = (userPreferences.lastSyncTime.firstOrNull() ?: 0L) == 0L

            // Recorded only here, after the write landed. Settings reads this to
            // say when steps last reached the server, so it must never count a
            // run that read Health Connect and then failed to save.
            userPreferences.saveLastSyncTime(System.currentTimeMillis())

            // ── Refresh each group's cached weekly total ─────────────────
            publishWeeklyTotals(user.userId, todaySteps, todayStr)

            // ── Smart Notifications Trigger ──────────────────────────────
            checkAndTriggerNotification(user.userId, todaySteps, isFirstEverSync)

            Result.success()
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            Log.e(TAG, "Sync failed | duration=${durationMs}ms | attempt=$runAttemptCount", e)
            analyticsManager.logStepsSyncFailed(user.userId, e.message ?: "unknown", durationMs)
            Result.retry()
        }
    }

    /**
     * Recomputes the combined weekly total for every group this user belongs to
     * and writes it onto the group document.
     *
     * This exists so the home screen can draw progress for a list of groups from
     * the groups it already loads. Deriving it there instead meant two live
     * Firestore listeners per group, on the screen opened most often, duplicating
     * the flatMapLatest chain that GroupViewModel had to have a nested-collect
     * bug fixed out of it. Here the same reads happen once per sync, in the
     * background, with no listeners left open.
     *
     * Failures are logged and swallowed on purpose. This is a display cache;
     * losing it costs a progress bar until the next sync, while retrying the
     * whole worker would re-read Health Connect and rewrite the step entry to
     * fix nothing.
     */
    private suspend fun publishWeeklyTotals(
        userId: String,
        todaySteps: Long,
        todayDate: String,
    ) {
        try {
            val groups = getUserGroupsUseCase(userId).firstOrNull().orEmpty()
            for (group in groups) {
                // This device publishes THIS user's figures and nobody else's.
                // The rules pin a membership write to its owner, and the steps
                // collection is now readable only by the person it belongs to,
                // so reading the group's other members here is neither possible
                // nor needed.
                //
                // Each group's own week, not this device's. A user can belong to
                // groups with different start days at the same time, and so has
                // a different weekly total in each.
                val myWeek = getMyStepsForWeekUseCase(userId, group.startDay)
                    .firstOrNull()
                    .orEmpty()

                publishMyWeeklyStepsUseCase(
                    groupId = group.groupId,
                    userId = userId,
                    steps = myWeek.sumOf { it.stepCount },
                    startDay = group.startDay,
                    todaySteps = todaySteps,
                    todayDate = todayDate,
                ).onFailure { Log.w(TAG, "Own total not published for ${group.groupId}", it) }

                // The group's cached total, recomputed from what every member has
                // published onto their own membership. A member who has not synced
                // recently contributes their last known figure, which was already
                // true when this read their step documents instead.
                val memberships = getGroupMembershipsUseCase(group.groupId)
                    .firstOrNull()
                    .orEmpty()
                if (memberships.isEmpty()) continue

                val thisWeek = WeekWindow.startOf(group.startDay)
                val groupTotal = memberships
                    .filter { it.weekStart == thisWeek }
                    .sumOf { it.weeklySteps }

                publishGroupWeeklyTotalUseCase(
                    group.groupId,
                    groupTotal,
                    group.startDay,
                ).onFailure { Log.w(TAG, "Weekly total not published for ${group.groupId}", it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Weekly totals refresh failed", e)
        }
    }

    private suspend fun checkAndTriggerNotification(
        userId: String,
        todaySteps: Long,
        isFirstEverSync: Boolean,
    ) {
        try {
            // Frequency, quiet hours, actionable-window and engagement backoff
            // are all decided inside SmartNotificationEngine, it needs the full
            // picture to make that call, so we only gather data here.
            val lastTime = userPreferences.lastNotificationTime.firstOrNull() ?: 0L
            val currentTime = System.currentTimeMillis()

            val activeGroupId = userPreferences.activeGroupId.firstOrNull()
            if (activeGroupId.isNullOrBlank()) {
                Log.d(TAG, "No active group set. Skipping notification check.")
                return
            }

            val groupResult = getGroupUseCase(activeGroupId)
            val group = groupResult.getOrNull() ?: return

            // Ranking comes from the memberships, which carry each member's own
            // published total. Reading their step documents is no longer possible
            // and no longer necessary.
            val memberships = getGroupMembershipsUseCase(activeGroupId).firstOrNull() ?: return
            if (memberships.isEmpty()) return

            // Calculate statistics
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val thisWeek = WeekWindow.startOf(group.startDay)

            // Map members to their weekly step counts
            data class MemberWeekly(val userId: String, val name: String, val steps: Long)
            val membersData = memberships.map { membership ->
                MemberWeekly(
                    membership.userId,
                    membership.displayName,
                    // Only counted when the marker says it is this week. A member
                    // who has not synced since the rollover carries last week's
                    // number, and ranking against that would be wrong.
                    membership.weeklySteps.takeIf { membership.weekStart == thisWeek } ?: 0L,
                )
            }.sortedByDescending { it.steps }

            // Find ranks and overtake targets
            val userIndex = membersData.indexOfFirst { it.userId == userId }
            val userRank = if (userIndex != -1) userIndex + 1 else membersData.size
            val teamAverage = if (membersData.isNotEmpty()) membersData.map { it.steps }.average().toLong() else 0L

            var stepsToOvertake = 0L
            var nextMemberName: String? = null
            if (userIndex > 0) {
                // Member at index - 1 is the one right ahead
                val targetMember = membersData[userIndex - 1]
                stepsToOvertake = (targetMember.steps - (membersData[userIndex].steps)).coerceAtLeast(0L)
                nextMemberName = targetMember.name
            }

            val userName = userPreferences.userName.firstOrNull() ?: "there"
            val weeklyGoal = group.weeklyTarget.toLong()
            val groupStepsTotal = membersData.sumOf { it.steps }
            val groupAgeMs = currentTime - group.createdAt
            val userStepsThisWeek = membersData.firstOrNull { it.userId == userId }?.steps ?: 0L

            // Days remaining in the group's week, inclusive of today. Was
            // `8 - today.dayOfWeek.value`, which silently assumed Monday and
            // would have told a Sunday-start group it had one day left on its
            // first day.
            val today = LocalDate.now()
            val daysLeftInWeek = WeekWindow.daysLeftIn(group.startDay, today)

            // Was the goal crossed by this sync? Compared against the total we
            // recorded last time so we fire exactly once, on the crossing.
            val previousTotal = userPreferences.lastKnownGroupTotal.firstOrNull() ?: 0L
            val goalReachedJustNow = weeklyGoal > 0 &&
                previousTotal < weeklyGoal &&
                groupStepsTotal >= weeklyGoal
            userPreferences.saveLastKnownGroupTotal(groupStepsTotal)

            // ── Detect locally that someone overtook this user ───────────────
            // No server needed: compare our leaderboard position against the one
            // recorded at the previous sync.
            //
            // Guarded on member count being unchanged. If someone joined or left,
            // everyone's rank shifts without anyone actually being overtaken, and
            // telling a user "Priya passed you" when Priya simply joined would be
            // both wrong and discouraging.
            val previousRank = userPreferences.lastKnownRank.firstOrNull() ?: 0
            val previousMemberCount = userPreferences.lastKnownMemberCount.firstOrNull() ?: 0

            val overtakenBy: String? = if (
                previousRank > 0 &&
                previousMemberCount == membersData.size &&
                userRank > previousRank &&
                userIndex > 0
            ) {
                // Whoever now sits directly ahead of us is the one who passed.
                membersData[userIndex - 1].name
            } else null

            // ── Somebody joined since the last sync ──────────────────────────
            //
            // Derived from the same stored member count the overtake guard uses,
            // so the two can never disagree: a change in size means a join, which
            // suppresses "you were overtaken" and reports the join instead.
            //
            // Requires a previous count above zero, or the very first sync would
            // announce the whole group as new arrivals. The caller is excluded,
            // because being told you joined is not news.
            val newMemberName: String? = if (
                previousMemberCount in 1 until membersData.size
            ) {
                memberships
                    .filter { it.userId != userId }
                    .maxByOrNull { it.joinedAt }
                    ?.displayName
                    ?.takeIf { it.isNotBlank() }
            } else null

            userPreferences.saveLeaderboardPosition(userRank, membersData.size)

            val context = NotificationContext(
                userName = userName,
                groupId = activeGroupId,
                groupName = group.name,
                memberCount = membersData.size,
                groupAgeMs = groupAgeMs,
                weeklyGoal = weeklyGoal,
                groupStepsTotal = groupStepsTotal,
                userStepsThisWeek = userStepsThisWeek,
                userStepsToday = todaySteps,
                daysLeftInWeek = daysLeftInWeek,
                memberJustAheadName = nextMemberName,
                stepsToOvertakeMemberAhead = stepsToOvertake,
                memberWhoJustPassedYouName = overtakenBy,
                goalReachedJustNow = goalReachedJustNow,
                newMemberName = newMemberName,
                isFirstEverSync = isFirstEverSync,
                // currentStreakDays and streakAtRiskToday are deliberately left
                // at their defaults, which makes the streak branch in
                // SmartNotificationEngine unreachable. Nothing records a streak:
                // there is no per-day goal and no history of past weeks to count
                // from. Wiring this needs that data to exist first, and the
                // engine branch is kept because it is what will read it.
                now = LocalTime.now(),
                today = today.dayOfWeek,
                hoursSinceLastNotification =
                    if (lastTime == 0L) Long.MAX_VALUE
                    else (currentTime - lastTime) / (60 * 60 * 1000L),
                consecutiveIgnored = userPreferences.consecutiveIgnoredNotifications.firstOrNull() ?: 0,
                // Day of year rotates the wording so repeated notification types
                // don't read identically every time.
                rotationSeed = today.dayOfYear,
                // Read from the Android notification channels, which are what
                // the user actually sets, in our Settings screen or in the
                // system's. Previously a DataStore copy, which could disagree
                // with the OS in either direction: a category muted in Android
                // that the engine still selected and posted into a void, or one
                // muted here that Android would happily have delivered.
                mutedCategories = smartNotificationHelper.mutedCategories(),
                quietStart = minuteOfDayToTime(
                    userPreferences.quietHoursStartMinute.firstOrNull() ?: (22 * 60)
                ),
                quietEnd = minuteOfDayToTime(
                    userPreferences.quietHoursEndMinute.firstOrNull() ?: (8 * 60)
                ),
            )

            val notification = SmartNotificationEngine.select(context)
            if (notification == null) {
                Log.d(TAG, "Engine chose to stay silent, nothing worth sending right now.")
                return
            }

            smartNotificationHelper.show(notification)
            userPreferences.saveLastNotificationTime(currentTime)
            // Optimistically count this as ignored; MainActivity resets the
            // counter to 0 when the user actually opens from a notification.
            userPreferences.incrementIgnoredNotifications()
            Log.i(TAG, "Notification sent [${notification.category}] ${notification.title}")

        } catch (e: Exception) {
            Log.e(TAG, "Error building notification", e)
        }
    }

    companion object {
        private const val TAG = "StepSyncWorker"
        const val WORK_NAME = "StepSyncWorker"
    }
}