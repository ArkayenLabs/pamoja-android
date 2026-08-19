package com.pamoja.app.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.pamoja.app.data.local.activity.ActivityLogStore
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.data.remote.firebase.FirebaseAuthRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseEmulator
import com.pamoja.app.data.remote.firebase.FirebaseGroupRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseStepRepositoryImpl
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.GetMyStepsForWeekUseCase
import com.pamoja.app.domain.usecase.GetUserGroupsUseCase
import com.pamoja.app.domain.usecase.PublishGroupWeeklyTotalUseCase
import com.pamoja.app.domain.usecase.PublishMyWeeklyStepsUseCase
import com.pamoja.app.util.PamojaNotification
import com.pamoja.app.util.SmartNotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * [StepSyncWorker], run for real against the Firestore emulator.
 *
 * The worker had no test coverage at all until now, while carrying the step
 * write, the publish-my-own-totals logic, and the group total recomputation.
 *
 * Real where real is possible: Firestore, Firebase Auth, the repositories, the
 * use cases and DataStore are all the production classes. Substituted only at
 * the two boundaries a test cannot drive:
 *
 *  - [HealthConnectReader], because Health Connect on a bare emulator has no
 *    granted permission and no recorded steps, so the real one always returns
 *    null and the worker would exit before doing anything worth testing. The
 *    double returns a fixed number, which is exactly what the real one returns
 *    on a device that does have data.
 *  - [SmartNotificationHelper], because posting is gated on a runtime
 *    permission and asserting on system notifications would test Android, not
 *    this worker.
 *
 * Requires the emulators, see [FirebaseEmulator].
 */
@RunWith(AndroidJUnit4::class)
class StepSyncWorkerEmulatorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clients = mutableListOf<FirebaseEmulator.Client>()

    private val today: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    @Before
    fun wipe() = runBlocking {
        FirebaseEmulator.reset()
        // DataStore is a real file shared by every test in this process, so a
        // leftover activeGroupId or sync time would leak between them.
        UserPreferences(context).clearAll()
    }

    @After
    fun closeClients() {
        clients.forEach { it.close() }
        clients.clear()
    }

    private suspend fun newUser(): FirebaseEmulator.Client =
        FirebaseEmulator.signIn().also { clients += it }

    /** Reports what Health Connect would, without needing Health Connect. */
    private class FakeHealthConnect(
        private val steps: Long?,
    ) : HealthConnectReader(InstrumentationRegistry.getInstrumentation().targetContext) {
        override suspend fun readTodaySteps(): Long? = steps
    }

    /** Records what the engine chose instead of posting it to the system. */
    private class RecordingNotifier(
        context: android.content.Context,
    ) : SmartNotificationHelper(context, ActivityLogStore(context)) {
        val posted = mutableListOf<PamojaNotification>()
        override fun show(notification: PamojaNotification) { posted += notification }
        override fun mutedCategories() = emptySet<com.pamoja.app.util.NotificationCategory>()
    }

    /**
     * Swallows analytics. The worker logs on every path and a test has no
     * business posting events to a real Firebase project.
     */
    private object NoopAnalytics : AnalyticsManager {
        override fun logEvent(eventName: String, params: Map<String, Any>?) = Unit
        override fun logWelcomeScreenViewed() = Unit
        override fun logProfileSetupStarted() = Unit
        override fun logProfileCompleted(userId: String) = Unit
        override fun logHealthConnectScreenViewed() = Unit
        override fun logHealthConnectPermissionRequested() = Unit
        override fun logHealthConnectPermissionGranted(userId: String) = Unit
        override fun logHealthConnectPermissionDenied() = Unit
        override fun logHealthConnectSkipped() = Unit
        override fun logHomeScreenReached() = Unit
        override fun logGroupCreated(groupId: String, groupName: String) = Unit
        override fun logGroupJoined(groupId: String, userId: String) = Unit
        override fun logGroupScreenViewed(groupId: String) = Unit
        override fun logWeeklyGoalReached(groupId: String, totalSteps: Long, target: Int) = Unit
        override fun logInviteScreenViewed(groupId: String) = Unit
        override fun logInviteLinkUsed(groupId: String, userId: String) = Unit
        override fun logStepsSyncStarted(userId: String) = Unit
        override fun logStepsSyncSuccess(userId: String, stepCount: Long, durationMs: Long) = Unit
        override fun logStepsSyncFailed(userId: String, reason: String, durationMs: Long) = Unit
        override fun logStepsSyncSkipped(userId: String, durationMs: Long) = Unit
    }

    /**
     * Builds the worker with the production graph wired by hand.
     *
     * Assembled explicitly rather than through Hilt so each test can choose the
     * identity and the step count, which is the whole variable under test.
     */
    private fun buildWorker(
        client: FirebaseEmulator.Client,
        steps: Long?,
        notifier: RecordingNotifier = RecordingNotifier(context),
    ): StepSyncWorker {
        val groups = FirebaseGroupRepositoryImpl(client.firestore)
        val stepsRepo = FirebaseStepRepositoryImpl(client.firestore, FakeHealthConnect(steps))
        val prefs = UserPreferences(context)

        return TestListenableWorkerBuilder<StepSyncWorker>(context)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: androidx.work.WorkerParameters,
                    ): ListenableWorker = StepSyncWorker(
                        appContext,
                        workerParameters,
                        authRepository = FirebaseAuthRepositoryImpl(client.auth),
                        stepRepository = stepsRepo,
                        healthConnectReader = FakeHealthConnect(steps),
                        analyticsManager = NoopAnalytics,
                        getGroupUseCase = GetGroupUseCase(groups),
                        getGroupMembershipsUseCase = GetGroupMembershipsUseCase(groups),
                        getMyStepsForWeekUseCase = GetMyStepsForWeekUseCase(stepsRepo),
                        publishMyWeeklyStepsUseCase = PublishMyWeeklyStepsUseCase(groups),
                        getUserGroupsUseCase = GetUserGroupsUseCase(groups),
                        publishGroupWeeklyTotalUseCase = PublishGroupWeeklyTotalUseCase(groups),
                        smartNotificationHelper = notifier,
                        userPreferences = prefs,
                    )
                }
            )
            .build()
    }

    private suspend fun createGroup(admin: FirebaseEmulator.Client): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "Worker test group",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
            weekStartDay = DayOfWeek.MONDAY.name,
        )
        FirebaseGroupRepositoryImpl(admin.firestore).createGroup(group).getOrThrow()
        return group
    }

    private suspend fun membership(client: FirebaseEmulator.Client, groupId: String, userId: String) =
        client.firestore.collection("memberships").document("${userId}_$groupId").get().await()

    // ── Guards ───────────────────────────────────────────────────────────────

    /** Nobody signed in means nothing to sync, and no amount of retrying fixes it. */
    @Test
    fun withNoSignedInUser_itFailsPermanently() = runBlocking {
        val client = newUser()
        client.auth.signOut()

        val result = buildWorker(client, steps = 5_000).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    /**
     * Health Connect being absent is not a failure.
     *
     * `CLAUDE.md` names this as a critical area: returning retry here would put
     * every device without Health Connect into an endless retry loop. It must
     * report success and write nothing.
     */
    @Test
    fun whenHealthConnectHasNothing_itSucceedsAndWritesNothing() = runBlocking {
        val user = newUser()

        val result = buildWorker(user, steps = null).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val entry = user.firestore.collection("steps")
            .document("${user.uid}_$today").get().await()
        assertTrue("Nothing should have been written, but the entry exists", !entry.exists())
    }

    // ── The sync itself ──────────────────────────────────────────────────────

    /** Exactly one step entry per run, keyed by user and date. */
    @Test
    fun aSuccessfulRun_writesTodaysStepEntry() = runBlocking {
        val user = newUser()

        val result = buildWorker(user, steps = 8_421).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val entry = user.firestore.collection("steps")
            .document("${user.uid}_$today").get().await()
        assertEquals(8_421L, entry.getLong("stepCount"))
        assertEquals(user.uid, entry.getString("userId"))
        assertEquals(today, entry.getString("date"))
    }

    /** Running twice must not accumulate, because the document id is stable. */
    @Test
    fun runningTwice_replacesRatherThanAccumulates() = runBlocking {
        val user = newUser()

        buildWorker(user, steps = 3_000).doWork()
        buildWorker(user, steps = 4_500).doWork()

        val entry = user.firestore.collection("steps")
            .document("${user.uid}_$today").get().await()
        assertEquals(4_500L, entry.getLong("stepCount"))
    }

    // ── Publishing onto the membership ───────────────────────────────────────

    /**
     * The figures the leaderboard reads are written here, onto the caller's own
     * membership, stamped with the markers that say which day and week they are
     * for.
     */
    @Test
    fun aSuccessfulRun_publishesMyTotalsOntoMyMembership() = runBlocking {
        val user = newUser()
        val group = createGroup(user)

        buildWorker(user, steps = 6_000).doWork()

        val mine = membership(user, group.groupId, user.uid)
        assertEquals(6_000L, mine.getLong("todaySteps"))
        assertEquals(today, mine.getString("todayDate"))
        assertEquals(6_000L, mine.getLong("weeklySteps"))
        assertEquals(WeekWindow.startOf(DayOfWeek.MONDAY), mine.getString("weekStart"))
    }

    /** And the group's cached total is recomputed from what members published. */
    @Test
    fun aSuccessfulRun_recomputesTheGroupTotal() = runBlocking {
        val alice = newUser()
        val bob = newUser()
        val group = createGroup(alice)
        FirebaseGroupRepositoryImpl(bob.firestore).joinGroup(group.groupId, bob.uid).getOrThrow()

        buildWorker(alice, steps = 5_000).doWork()
        buildWorker(bob, steps = 2_500).doWork()

        val doc = alice.firestore.collection("groups").document(group.groupId).get().await()
        assertEquals(7_500L, doc.getLong("weeklySteps"))
        assertEquals(WeekWindow.startOf(DayOfWeek.MONDAY), doc.getString("weekStart"))
    }

    /**
     * A member's own device writes their row and nobody else's.
     *
     * This is the invariant that let the steps collection become private: if the
     * worker still needed to write other people's figures it would still need to
     * read their step history.
     */
    @Test
    fun aRun_leavesOtherMembersRowsAlone() = runBlocking {
        val alice = newUser()
        val bob = newUser()
        val group = createGroup(alice)
        FirebaseGroupRepositoryImpl(bob.firestore).joinGroup(group.groupId, bob.uid).getOrThrow()

        buildWorker(alice, steps = 9_000).doWork()

        val bobsRow = membership(alice, group.groupId, bob.uid)
        assertEquals(0L, bobsRow.getLong("todaySteps"))
        assertEquals("", bobsRow.getString("weekStart"))
    }

    /** Belonging to several groups publishes into each of them. */
    @Test
    fun aRun_publishesIntoEveryGroupTheUserBelongsTo() = runBlocking {
        val user = newUser()
        val first = createGroup(user)
        val second = createGroup(user)

        buildWorker(user, steps = 4_000).doWork()

        assertEquals(4_000L, membership(user, first.groupId, user.uid).getLong("weeklySteps"))
        assertEquals(4_000L, membership(user, second.groupId, user.uid).getLong("weeklySteps"))
    }

    /** Someone in no group at all still syncs, and simply has nowhere to publish. */
    @Test
    fun aUserInNoGroups_stillSyncsWithoutFailing() = runBlocking {
        val user = newUser()

        val result = buildWorker(user, steps = 1_200).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            1_200L,
            user.firestore.collection("steps")
                .document("${user.uid}_$today").get().await().getLong("stepCount"),
        )
    }
}
