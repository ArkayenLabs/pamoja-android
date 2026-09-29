package com.pamoja.app.widgets

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.analytics.AnalyticsEvent
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.analytics.AnalyticsParam
import com.pamoja.app.domain.model.PamojaGroupId
import com.pamoja.app.domain.model.SignalKind
import com.pamoja.app.domain.model.UsualSteps
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetUserGroupsUseCase
import com.pamoja.app.domain.usecase.ObserveReceivedSignalsUseCase
import com.pamoja.app.domain.usecase.ObserveSentSignalsUseCase
import com.pamoja.app.domain.usecase.SendSignalUseCase
import com.pamoja.app.ui.home.chooseGroup
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one way anything asks the widgets to catch up. Every caller (a step
 * sync, Home, a cheer arriving, a send from a widget) enqueues the same unique
 * [WidgetRefreshWorker], so a burst of them costs one read.
 */
@Singleton
class PamojaWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun refresh() = enqueueRefresh(context)

    fun clear() {
        WorkManager.getInstance(context).cancelUniqueWork(REFRESH_WORK)
        WidgetStore.clear(context)
        PamojaWidgetRenderer.updateAll(context)
    }
}

private const val REFRESH_WORK = "widget-refresh"
private const val TAG = "PamojaWidgets"

/**
 * Nothing at all when no widget is placed, which is most people. Otherwise one
 * run a few seconds after the first request: Home asks on every step update,
 * and KEEP folds everything that arrives meanwhile into that one run. Without
 * the delay each run finished at once and the next request started another,
 * a read every second or two while Home was open. A widget just placed or
 * just tapped passes 0: it should not sit stale for the delay.
 */
internal fun enqueueRefresh(context: Context, delaySeconds: Long = REFRESH_DELAY_SECONDS) {
    if (!PamojaWidgetRenderer.anyPlaced(context)) return
    WorkManager.getInstance(context).enqueueUniqueWork(
        REFRESH_WORK,
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build(),
    )
}

private const val REFRESH_DELAY_SECONDS = 5L

/**
 * Reads what the widgets draw and stores it as a [WidgetSnapshot]: the group
 * Home shows, its members' figures, today's cheers and nudges, and this
 * user's own steps. Reads nothing Home does not already read.
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val getCurrentUser: GetCurrentUserUseCase,
    private val getUserGroups: GetUserGroupsUseCase,
    private val getGroupMemberships: GetGroupMembershipsUseCase,
    private val observeReceivedSignals: ObserveReceivedSignalsUseCase,
    private val observeSentSignals: ObserveSentSignalsUseCase,
    private val healthConnectReader: HealthConnectReader,
    private val userPreferences: UserPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val user = getCurrentUser() ?: run {
            WidgetStore.clear(applicationContext)
            PamojaWidgetRenderer.updateAll(applicationContext)
            return Result.success()
        }
        // Nobody has a widget placed: nothing to spend reads on.
        if (!PamojaWidgetRenderer.anyPlaced(applicationContext)) return Result.success()

        return try {
            val today = LocalDate.now()
            val todayText = today.toString()
            // Nothing at all (offline with an empty cache, or refused): keep
            // the last snapshot rather than tell the widget there is no group.
            val groups = latest(getUserGroups(user.userId))
                ?: return if (runAttemptCount < 2) Result.retry() else Result.success()
            val active = userPreferences.activeGroupId.first()?.takeIf(PamojaGroupId::isValid)
            val group = chooseGroup(active, groups, gone = emptySet())?.let { id -> groups.firstOrNull { it.groupId == id } }

            val (members, received, sent) = if (group == null) {
                Triple(emptyList(), emptyList(), emptyList())
            } else coroutineScope {
                val members = async { latest(getGroupMemberships(group.groupId)) }
                val received = async { latest(observeReceivedSignals(group.groupId, user.userId, todayText)) }
                val sent = async { latest(observeSentSignals(group.groupId, user.userId, todayText)) }
                Triple(members.await().orEmpty(), received.await().orEmpty(), sent.await().orEmpty())
            }

            val todaySteps = healthConnectReader.readTodaySteps()
            val usual = healthConnectReader.readDailySteps(today.minusDays(USUAL_HISTORY_DAYS), today)
                ?.let(UsualSteps::of)

            val snapshot = WidgetSnapshot(
                userId = user.userId,
                group = group,
                members = members,
                signalsDate = todayText,
                cheersFrom = received.filter { it.kind == SignalKind.CHEER }.map { it.fromUserId },
                cheeredBack = sent.filter { it.kind == SignalKind.CHEER }.map { it.toUserId }.toSet(),
                nudged = sent.filter { it.kind == SignalKind.NUDGE }.map { it.toUserId }.toSet(),
                todaySteps = todaySteps,
                stepsDate = todayText,
                usualSteps = usual,
                refreshedAt = System.currentTimeMillis(),
            )
            cacheFaces(snapshot)
            WidgetStore.write(applicationContext, snapshot)
            PamojaWidgetRenderer.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // The type only; Firebase messages carry document paths.
            Log.w(TAG, "Widget refresh failed: ${e::class.java.simpleName}")
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    /**
     * Listeners answer from the local cache first and the server a moment
     * later; the widget wants the later answer when there is one, but never
     * waits long for it. A read that fails keeps whatever arrived before it.
     */
    private suspend fun <T> latest(flow: Flow<T>): T? {
        var last: T? = null
        withTimeoutOrNull(SERVER_WAIT_MS) { flow.catch { }.collect { last = it } }
        return last
    }

    /** Downloads the photos of the faces a widget may show; initials cover the rest. */
    private suspend fun cacheFaces(snapshot: WidgetSnapshot) {
        val dir = WidgetStore.facesDir(applicationContext).apply { mkdirs() }
        val wanted = snapshot.members.filter { it.photoUrl.isNotBlank() }
        val keep = wanted.map { WidgetStore.faceFile(applicationContext, it.userId).name }.toSet()
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        wanted.forEach { member ->
            val file = WidgetStore.faceFile(applicationContext, member.userId)
            val request = ImageRequest.Builder(applicationContext)
                .data(member.photoUrl)
                .size(FACE_PX)
                .allowHardware(false)
                .build()
            val result = applicationContext.imageLoader.execute(request) as? SuccessResult ?: return@forEach
            runCatching {
                FileOutputStream(file).use { result.drawable.toBitmap(FACE_PX, FACE_PX).compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.onFailure { file.delete() }
        }
    }

    private companion object {
        const val SERVER_WAIT_MS = 4_000L
        const val USUAL_HISTORY_DAYS = 14L
        const val FACE_PX = 128
    }
}

/**
 * A cheer or nudge tapped on a widget. Sent through WorkManager so it is not
 * lost when the phone is offline for a moment, then the widgets are refreshed
 * from what the server really holds, which undoes the optimistic "Nudged"
 * if the send was refused.
 */
@HiltWorker
class WidgetSignalWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val getCurrentUser: GetCurrentUserUseCase,
    private val observeSentSignals: ObserveSentSignalsUseCase,
    private val sendSignal: SendSignalUseCase,
    private val analyticsManager: AnalyticsManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = SignalKind.fromWire(inputData.getString(KEY_KIND).orEmpty()) ?: return Result.failure()
        val groupId = inputData.getString(KEY_GROUP).orEmpty()
        val date = inputData.getString(KEY_DATE).orEmpty()
        val to = inputData.getStringArray(KEY_TO).orEmpty().toList()
        val user = getCurrentUser()
        // Signed out, or another account since the tap: never send as them.
        if (user == null || user.userId != inputData.getString(KEY_FROM)) {
            enqueueRefresh(applicationContext)
            return Result.failure()
        }
        // A cheer or nudge belongs to its day; tomorrow it would be a different one.
        if (date != LocalDate.now().toString()) {
            enqueueRefresh(applicationContext)
            return Result.failure()
        }

        val alreadySent = withTimeoutOrNull(4_000L) { observeSentSignals(groupId, user.userId, date).first() }.orEmpty()
        val result = sendSignal(groupId, kind, user.userId, to, date, alreadySent)
        enqueueRefresh(applicationContext, delaySeconds = 0)
        return result.fold(
            onSuccess = {
                analyticsManager.logEvent(
                    if (kind == SignalKind.CHEER) AnalyticsEvent.CHEER_SENT else AnalyticsEvent.NUDGE_SENT,
                    mapOf(AnalyticsParam.GROUP_ID to groupId, AnalyticsParam.RECIPIENTS to to.size),
                )
                Result.success()
            },
            onFailure = { if (runAttemptCount < 2) Result.retry() else Result.failure() },
        )
    }

    companion object {
        private const val KEY_KIND = "kind"
        private const val KEY_GROUP = "group"
        private const val KEY_FROM = "from"
        private const val KEY_TO = "to"
        private const val KEY_DATE = "date"

        fun enqueue(context: Context, kind: SignalKind, groupId: String, from: String, to: List<String>, date: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "widget-signal-${kind.wire}-$groupId-$date",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<WidgetSignalWorker>()
                    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                    .setInputData(
                        workDataOf(
                            KEY_KIND to kind.wire,
                            KEY_GROUP to groupId,
                            KEY_FROM to from,
                            KEY_TO to to.toTypedArray(),
                            KEY_DATE to date,
                        ),
                    )
                    .build(),
            )
        }
    }
}
