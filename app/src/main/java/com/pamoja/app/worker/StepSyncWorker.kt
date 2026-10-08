package com.pamoja.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.model.WalkedDays
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
import com.pamoja.app.util.NotificationCopy
import com.pamoja.app.util.minuteOfDayToTime
import com.pamoja.app.widgets.PamojaWidgetUpdater
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
    private val userPreferences: UserPreferences,
    private val widgetUpdater: PamojaWidgetUpdater,
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

        analyticsManager.logStepsSyncStarted()

        return try {
            // ── Read today's steps from Health Connect ───────────────────
            // Returns null if HC unavailable or permission revoked, silent success, no retry
            val todaySteps = healthConnectReader.readTodaySteps()
            if (todaySteps == null) {
                val durationMs = System.currentTimeMillis() - startTime
                Log.w(TAG, "Health Connect returned null (unavailable/revoked) | duration=${durationMs}ms")
                analyticsManager.logStepsSyncSkipped(durationMs)
                return Result.success()
            }

            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            // The count itself is deliberately not logged. logcat is readable by
            // anyone holding the device with debugging on, and is bundled into
            // `adb bugreport` output, so a step reading in here is a Health
            // Connect value leaving the app by a route nobody audits. Log that a
            // read happened, never what it said. Same rule as AnalyticsManager.
            Log.d(TAG, "Read steps for $todayStr")

            // ── Write to Firestore (one write per worker execution) ──────
            val entry = StepEntry(
                userId    = user.userId,
                stepCount = todaySteps,
                date      = todayStr
            )
            stepRepository.saveStepEntry(entry).getOrElse { throw it }

            // Once per account/day, recover days missed while the app was closed.
            // A partial read or failed write leaves the checkpoint unset for retry.
            if (!userPreferences.hasCaughtUpSteps(user.userId, todayStr)) {
                val recovered = com.pamoja.app.domain.usecase.recoverRecentSteps(
                    today = LocalDate.parse(todayStr),
                    read = healthConnectReader::readStepsForDate,
                    save = { date, steps ->
                        stepRepository.saveStepEntry(StepEntry(
                            userId = user.userId, date = date.toString(), stepCount = steps,
                        )).getOrThrow()
                    },
                )
                if (recovered) userPreferences.markStepsCaughtUp(user.userId, todayStr)
            }

            val durationMs = System.currentTimeMillis() - startTime
            Log.i(TAG, "Sync success | duration=${durationMs}ms")
            analyticsManager.logStepsSyncSuccess(durationMs)

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
            widgetUpdater.refresh()

            // ── Smart Notifications Trigger ──────────────────────────────
            checkAndTriggerNotification(user.userId, todaySteps, isFirstEverSync)

            Result.success()
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            Log.e(TAG, "Sync failed | duration=${durationMs}ms | attempt=$runAttemptCount", e)
            // The exception's TYPE, never its message. Firebase messages carry
            // document paths, and this is the same rule AppError follows: match
            // on type, never on message text.
            analyticsManager.logStepsSyncFailed(
                e::class.java.simpleName ?: "unknown", durationMs
            )
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
                val reportingDate = WeekWindow.todayFor(group)
                val myWeek = getMyStepsForWeekUseCase(userId, group.startDay, reportingDate)
                    .firstOrNull()
                    .orEmpty()

                publishMyWeeklyStepsUseCase(
                    groupId = group.groupId,
                    userId = userId,
                    steps = myWeek.sumOf { it.stepCount },
                    startDay = group.startDay,
                    todaySteps = todaySteps,
                    todayDate = todayDate,
                    // The same entries and the same week, so the dots can
                    // never disagree with the total beside them.
                    walkedDays = WalkedDays.of(
                        myWeek,
                        LocalDate.parse(WeekWindow.startOf(group.startDay, reportingDate)),
                    ),
                    reportingDate = reportingDate,
                ).onFailure { Log.w(TAG, "Own total not published for ${group.groupId}", it) }

                // The group's cached total, recomputed from what every member has
                // published onto their own membership. A member who has not synced
                // recently contributes their last known figure, which was already
                // true when this read their step documents instead.
                val memberships = getGroupMembershipsUseCase(group.groupId)
                    .firstOrNull()
                    .orEmpty()
                if (memberships.isEmpty()) continue

                val thisWeek = WeekWindow.startOf(group.startDay, reportingDate)
                val groupTotal = memberships
                    .filter { it.weekStart == thisWeek }
                    .sumOf { it.weeklySteps }

                publishGroupWeeklyTotalUseCase(
                    group.groupId,
                    groupTotal,
                    group.startDay,
                    reportingDate,
                ).onFailure { Log.w(TAG, "Weekly total not published for ${group.groupId}", it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Weekly totals refresh failed", e)
        }
    }

    /**
     * The one notification this phone still decides for itself: confirming
     * the first sync. Reminders, recaps and everything about other people
     * come from functions-notifications (NOTIFICATIONS_PLAN.md), so nothing
     * is read here on any later sync.
     */
    private suspend fun checkAndTriggerNotification(
        userId: String,
        todaySteps: Long,
        isFirstEverSync: Boolean,
    ) {
        if (!isFirstEverSync) return
        try {
            // Not only the group last opened: a person who created or joined a
            // group and stayed on Home never saved one. Resolve the preference
            // against current memberships and choose a safe fallback.
            val groups = getUserGroupsUseCase(userId).firstOrNull().orEmpty()
            if (groups.isEmpty()) {
                Log.d(TAG, "No groups available. Skipping notification check.")
                return
            }
            val preferredGroupId = userPreferences.activeGroupId.firstOrNull()
            val group = groups.firstOrNull { it.groupId == preferredGroupId } ?: groups.first()

            val notice = SmartNotificationEngine.select(
                NotificationContext(
                    groupId = group.groupId,
                    groupName = group.name,
                    userStepsToday = todaySteps,
                    isFirstEverSync = true,
                    now = LocalTime.now(),
                    // Read from the Android notification channels, which are
                    // what the user actually sets, here or in system settings.
                    mutedCategories = smartNotificationHelper.mutedCategories(),
                    quietStart = minuteOfDayToTime(
                        userPreferences.quietHoursStartMinute.firstOrNull() ?: (22 * 60)
                    ),
                    quietEnd = minuteOfDayToTime(
                        userPreferences.quietHoursEndMinute.firstOrNull() ?: (8 * 60)
                    ),
                )
            ) ?: return

            smartNotificationHelper.show(NotificationCopy.render(applicationContext, notice))
            // The category, never the text: the body names a group, and logcat
            // is readable with debugging on and bundled into `adb bugreport`.
            Log.i(TAG, "Notification sent [${notice.category}]")
        } catch (e: Exception) {
            Log.e(TAG, "Error building notification", e)
        }
    }

    companion object {
        private const val TAG = "StepSyncWorker"
        const val WORK_NAME = "StepSyncWorker"
    }
}
