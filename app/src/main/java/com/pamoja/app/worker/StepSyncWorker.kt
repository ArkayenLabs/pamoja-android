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
import com.pamoja.app.domain.usecase.GetGroupMembersUseCase
import com.pamoja.app.domain.usecase.GetGroupStepsForWeekUseCase
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
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    private val getGroupStepsForWeekUseCase: GetGroupStepsForWeekUseCase,
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

            // Recorded only here, after the write landed. Settings reads this to
            // say when steps last reached the server, so it must never count a
            // run that read Health Connect and then failed to save.
            userPreferences.saveLastSyncTime(System.currentTimeMillis())

            // ── Refresh each group's cached weekly total ─────────────────
            publishWeeklyTotals(user.userId)

            // ── Smart Notifications Trigger ──────────────────────────────
            checkAndTriggerNotification(user.userId, todaySteps)

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
    private suspend fun publishWeeklyTotals(userId: String) {
        try {
            val groups = getUserGroupsUseCase(userId).firstOrNull().orEmpty()
            for (group in groups) {
                val members = getGroupMembersUseCase(group.groupId).firstOrNull().orEmpty()
                if (members.isEmpty()) continue
                // Each group's own week, not this device's. A user can belong to
                // groups with different start days at the same time.
                val entries = getGroupStepsForWeekUseCase(
                    members.map { it.userId },
                    group.startDay,
                ).firstOrNull().orEmpty()
                publishGroupWeeklyTotalUseCase(
                    group.groupId,
                    entries.sumOf { it.stepCount },
                    group.startDay,
                ).onFailure { Log.w(TAG, "Weekly total not published for ${group.groupId}", it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Weekly totals refresh failed", e)
        }
    }

    private suspend fun checkAndTriggerNotification(userId: String, todaySteps: Long) {
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

            val members = getGroupMembersUseCase(activeGroupId).firstOrNull() ?: return
            if (members.isEmpty()) return

            val memberIds = members.map { it.userId }
            val weeklyEntries =
                getGroupStepsForWeekUseCase(memberIds, group.startDay).firstOrNull() ?: return

            // Calculate statistics
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            // Map members to their weekly step counts
            data class MemberWeekly(val userId: String, val name: String, val steps: Long)
            val membersData = members.map { member ->
                val steps = weeklyEntries
                    .filter { it.userId == member.userId }
                    .sumOf { it.stepCount }
                MemberWeekly(member.userId, member.name, steps)
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
            val groupStepsTotal = weeklyEntries.sumOf { it.stepCount }
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