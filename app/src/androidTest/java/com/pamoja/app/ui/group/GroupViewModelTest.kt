package com.pamoja.app.ui.group

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.repository.AuthMethods
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.domain.repository.PhoneVerification
import com.pamoja.app.domain.repository.StepRepository
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.GetMembershipUseCase
import com.pamoja.app.domain.usecase.GetStepsForUserUseCase
import com.pamoja.app.domain.usecase.SyncTodayStepsUseCase
import com.pamoja.app.domain.usecase.UpdateWeeklyTargetUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * [GroupViewModel], the first test above the data layer in this project.
 *
 * Instrumented rather than a JVM unit test for one reason: the ViewModel is
 * handed [UserPreferences] and [ConnectivityObserver] directly, both of which
 * need an `android.content.Context`, and a Context cannot be constructed on a
 * plain JVM at all. Making them `open` does not help, since a subclass still
 * has to pass one to the superclass constructor.
 *
 * So the split here is:
 *
 *  - **Real**: [UserPreferences] against the device's own DataStore, every use
 *    case, and the ViewModel itself.
 *  - **Faked**: the three repositories, because the whole point is to drive the
 *    ViewModel's branches from inputs a test chooses. They are interfaces, so
 *    no production change was needed for them.
 *  - **Open**: [ConnectivityObserver], so a test can say "offline". The real one
 *    reports what the device is actually doing, which a test cannot drive. Same
 *    reason [HealthConnectReader] was made open for the worker tests.
 *
 * Unlike the other instrumented tests here, this one needs **no emulator**. It
 * touches no network.
 *
 * What is deliberately not asserted: `lastSyncedAt`. It is a pass-through of a
 * real DataStore flow whose writes land on Dispatchers.IO, so asserting on it
 * would buy a trivial assertion at the price of a flaky test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GroupViewModelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val today: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private val yesterday: String =
        LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
    private val thisWeek: String = WeekWindow.startOf(DayOfWeek.MONDAY)
    private val lastWeek: String =
        WeekWindow.startOf(DayOfWeek.MONDAY, LocalDate.now().minusWeeks(1))

    private val groupId = "group-1"
    private val meId = "me"

    private val group = Group(
        groupId = groupId,
        name = "Test group",
        adminId = meId,
        weeklyTarget = 70_000,
        maxMemberCap = 10,
        memberCount = 2,
        weekStartDay = DayOfWeek.MONDAY.name,
    )

    private lateinit var groups: FakeGroupRepository
    private lateinit var auth: FakeAuthRepository
    private lateinit var steps: FakeStepRepository
    private lateinit var connectivity: FakeConnectivity
    private lateinit var analytics: RecordingAnalytics
    private lateinit var prefs: UserPreferences

    @Before
    fun setUp() = runBlocking {
        // A real dispatcher, not a TestDispatcher, on purpose. Real DataStore
        // IO sits inside loadGroup, and virtual time does not wait for it, so a
        // scheduler-driven test would assert against a half-finished load. Here
        // every delay is real and [awaitState] does the waiting instead.
        //
        // Not Dispatchers.Unconfined either, which looks like the obvious
        // choice and is not: setMain wraps it in a TestMainDispatcher that
        // really does call dispatch, and Unconfined throws on any dispatch it
        // did not originate. It surfaces as DataStore's StateFlow resuming
        // across the main dispatcher, in whichever test happens to write first.
        Dispatchers.setMain(Dispatchers.Default)

        // DataStore is one real file shared by every test in this process.
        UserPreferences(context).clearAll()

        groups = FakeGroupRepository()
        auth = FakeAuthRepository()
        steps = FakeStepRepository()
        connectivity = FakeConnectivity(context)
        analytics = RecordingAnalytics()
        prefs = UserPreferences(context)

        groups.groupResult = Result.success(group)
        groups.membershipResult = Result.success(
            Membership(userId = meId, groupId = groupId, role = "admin")
        )
        auth.currentUser = User(userId = meId, name = "Me")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GroupViewModel(
        getGroupUseCase = GetGroupUseCase(groups),
        getGroupMembershipsUseCase = GetGroupMembershipsUseCase(groups),
        getStepsForUserUseCase = GetStepsForUserUseCase(steps),
        getMembershipUseCase = GetMembershipUseCase(groups),
        getCurrentUserUseCase = GetCurrentUserUseCase(auth),
        updateWeeklyTargetUseCase = UpdateWeeklyTargetUseCase(groups),
        userPreferences = prefs,
        healthConnectReader = HealthConnectReader(context),
        analyticsManager = analytics,
        syncTodayStepsUseCase = SyncTodayStepsUseCase(steps),
        connectivityObserver = connectivity,
    )

    /**
     * Polls until the state satisfies [predicate], or fails saying what it was
     * still waiting for. Needed because real DataStore writes sit in the middle
     * of loadGroup, so there is no "idle" moment a scheduler could identify.
     */
    private suspend fun GroupViewModel.awaitState(
        describe: String,
        timeoutMs: Long = 5_000,
        predicate: (GroupUiState) -> Boolean,
    ): GroupUiState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = uiState.value
            if (predicate(state)) return state
            delay(10)
        }
        fail("Timed out waiting for $describe. Last state was ${uiState.value}")
        error("unreachable")
    }

    private fun member(
        userId: String,
        name: String,
        todaySteps: Long = 0L,
        todayDate: String = today,
        weeklySteps: Long = 0L,
        weekStart: String = thisWeek,
    ) = Membership(
        userId = userId,
        groupId = groupId,
        displayName = name,
        todaySteps = todaySteps,
        todayDate = todayDate,
        weeklySteps = weeklySteps,
        weekStart = weekStart,
    )

    // ── The denormalisation's one way to lie: stale markers ──────────────────

    /**
     * The figure is real but belongs to yesterday. Showing it as today's is
     * exactly the failure the `todayDate` marker exists to prevent.
     */
    @Test
    fun todayStepsFromAnEarlierDayReadAsZero() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)
        groups.memberships.emit(
            listOf(member(meId, "Me", todaySteps = 9_000, todayDate = yesterday))
        )

        val state = vm.awaitState("members to arrive") { it.hasLoadedMembers }

        assertEquals(1, state.memberStepData.size)
        assertEquals(0L, state.memberStepData.first().todaySteps)
    }

    @Test
    fun weeklyStepsFromAnEarlierWeekReadAsZero() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)
        groups.memberships.emit(
            listOf(member(meId, "Me", weeklySteps = 50_000, weekStart = lastWeek))
        )

        val state = vm.awaitState("members to arrive") { it.hasLoadedMembers }

        assertEquals(0L, state.memberStepData.first().weeklySteps)
        assertEquals(0L, state.combinedWeeklySteps)
    }

    @Test
    fun currentFiguresSurviveTheMarkerCheck() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)
        groups.memberships.emit(
            listOf(member(meId, "Me", todaySteps = 8_000, weeklySteps = 40_000))
        )

        val state = vm.awaitState("members to arrive") { it.hasLoadedMembers }

        assertEquals(8_000L, state.memberStepData.first().todaySteps)
        assertEquals(40_000L, state.memberStepData.first().weeklySteps)
        assertEquals(40_000L, state.combinedWeeklySteps)
    }

    /**
     * A member who has not synced since the rollover must not drag the group
     * total up with last week's number.
     */
    @Test
    fun combinedWeeklyTotalCountsOnlyCurrentFigures() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)
        groups.memberships.emit(
            listOf(
                member(meId, "Me", weeklySteps = 30_000),
                member("them", "Them", weeklySteps = 25_000, weekStart = lastWeek),
            )
        )

        val state = vm.awaitState("members to arrive") { it.hasLoadedMembers }

        assertEquals(30_000L, state.combinedWeeklySteps)
    }

    // ── Leaderboard ordering ────────────────────────────────────────────────

    @Test
    fun leaderboardOrdersByTodayStepsDescending() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)
        groups.memberships.emit(
            listOf(
                member("c", "Cee", todaySteps = 1_000),
                member("a", "Ay", todaySteps = 9_000),
                member("b", "Bee", todaySteps = 5_000),
            )
        )

        val state = vm.awaitState("members to arrive") { it.hasLoadedMembers }

        assertEquals(listOf("Ay", "Bee", "Cee"), state.memberStepData.map { it.user.name })
    }

    /** Everyone on zero is the hopeful empty state, not an error. */
    @Test
    fun everyoneOnZeroIsNoStepsYetRatherThanAnError() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)
        groups.memberships.emit(listOf(member(meId, "Me"), member("them", "Them")))

        val state = vm.awaitState("members to arrive") { it.hasLoadedMembers }

        assertTrue(state.showNoStepsYet)
        assertNull(state.stepsError)
    }

    // ── Failure paths ───────────────────────────────────────────────────────

    /**
     * The specific regression the ViewModel carries a comment about: setting
     * only the error left the default `isLoading = true` in place, and since
     * the screen checks loading first, every failure rendered as a spinner that
     * never stopped.
     */
    @Test
    fun aSignedOutUserStopsLoadingRatherThanSpinningForever() = runBlocking {
        auth.currentUser = null

        val vm = viewModel()
        vm.loadGroup(groupId)

        val state = vm.awaitState("the failure to land") { it.fatalError != null }

        assertTrue(state.fatalError is AppError.SessionExpired)
        assertFalse(state.isLoading)
        assertFalse(state.showSkeleton)
    }

    @Test
    fun aMissingGroupIsTerminalRatherThanRetryable() = runBlocking {
        groups.groupResult = Result.failure(AppError.NotFound())

        val vm = viewModel()
        vm.loadGroup(groupId)

        val state = vm.awaitState("the failure to land") { it.fatalError != null }

        assertTrue(state.isGroupUnavailable)
        assertFalse(state.isLoading)
    }

    /**
     * Being removed from a group and the group being deleted are
     * indistinguishable from the client, so both must end the same way.
     */
    @Test
    fun beingDeniedTheGroupIsAlsoTerminal() = runBlocking {
        groups.groupResult = Result.failure(AppError.PermissionDenied())

        val vm = viewModel()
        vm.loadGroup(groupId)

        val state = vm.awaitState("the failure to land") { it.fatalError != null }

        assertTrue(state.isGroupUnavailable)
    }

    /** A network failure is worth a Retry, so it must not be marked terminal. */
    @Test
    fun aNetworkFailureStaysRetryable() = runBlocking {
        groups.groupResult = Result.failure(AppError.Network())

        val vm = viewModel()
        vm.loadGroup(groupId)

        val state = vm.awaitState("the failure to land") { it.fatalError != null }

        assertTrue(state.fatalError is AppError.Network)
        assertFalse(state.isGroupUnavailable)
    }

    @Test
    fun aFailingMembershipStreamSurfacesAsAFatalError() = runBlocking {
        groups.membershipsOverride = flow { throw AppError.PermissionDenied() }

        val vm = viewModel()
        vm.loadGroup(groupId)

        val state = vm.awaitState("the stream failure to land") { it.fatalError != null }

        assertTrue(state.fatalError is AppError.PermissionDenied)
        assertTrue(state.isGroupUnavailable)
        assertFalse(state.isLoading)
    }

    // ── Analytics ───────────────────────────────────────────────────────────

    /**
     * Snapshot listeners re-emit on every membership write, so a goal event
     * guarded only by the threshold would fire on each one.
     */
    @Test
    fun theWeeklyGoalIsLoggedOnceNoMatterHowManySnapshotsArrive() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)

        groups.memberships.emit(listOf(member(meId, "Me", weeklySteps = 80_000)))
        vm.awaitState("the first snapshot") { it.combinedWeeklySteps == 80_000L }

        groups.memberships.emit(listOf(member(meId, "Me", weeklySteps = 90_000)))
        vm.awaitState("the second snapshot") { it.combinedWeeklySteps == 90_000L }

        assertEquals(1, analytics.weeklyGoalReached.size)
    }

    @Test
    fun theWeeklyGoalIsNotLoggedBelowTheTarget() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)

        groups.memberships.emit(listOf(member(meId, "Me", weeklySteps = 69_999)))
        vm.awaitState("the snapshot") { it.hasLoadedMembers }

        assertEquals(0, analytics.weeklyGoalReached.size)
    }

    // ── Changing the target ─────────────────────────────────────────────────

    @Test
    fun aSuccessfulTargetChangeIsReflectedInTheGroup() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)
        vm.awaitState("the group to load") { it.group != null }

        vm.updateWeeklyTarget(100_000)

        val state = vm.awaitState("the new target") { it.group?.weeklyTarget == 100_000 }

        assertEquals(100_000, groups.updatedTarget)
        assertNull(state.actionError)
    }

    /**
     * A failed target change is a snackbar, not a screen. The group already on
     * screen must survive it, and must not show the target that did not save.
     */
    @Test
    fun aFailedTargetChangeKeepsTheOldTargetAndOnlyRaisesASnackbar() = runBlocking {
        groups.updateTargetResult = Result.failure(AppError.Network())

        val vm = viewModel()
        vm.loadGroup(groupId)
        vm.awaitState("the group to load") { it.group != null }

        vm.updateWeeklyTarget(100_000)

        val state = vm.awaitState("the action error") { it.actionError != null }

        assertTrue(state.actionError is AppError.Network)
        assertEquals(70_000, state.group?.weeklyTarget)
        assertNull(state.fatalError)

        vm.clearActionError()
        assertNull(vm.uiState.value.actionError)
    }

    /** A member may not move the target unless the admin allowed it. */
    @Test
    fun aMemberCannotChangeTheTargetWhenTheAdminHasNotAllowedIt() = runBlocking {
        groups.groupResult = Result.success(
            group.copy(adminId = "someone-else", canMembersEditTarget = false)
        )

        val vm = viewModel()
        vm.loadGroup(groupId)
        vm.awaitState("the group to load") { it.group != null }

        vm.updateWeeklyTarget(100_000)

        val state = vm.awaitState("the action error") { it.actionError != null }

        val error = state.actionError
        assertTrue(error is AppError.Validation)
        assertEquals(ValidationField.NotAllowedToEditTarget, (error as AppError.Validation).field)
        assertNull(groups.updatedTarget)
    }

    @Test
    fun theAdminIsRecognisedAsSuch() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)

        val state = vm.awaitState("the group to load") { it.group != null }

        assertTrue(state.isAdmin)
        assertEquals(meId, state.currentUserId)
    }

    // ── Connectivity ────────────────────────────────────────────────────────

    @Test
    fun goingOfflineIsReflectedInTheState() = runBlocking {
        val vm = viewModel()
        vm.awaitState("the initial online state") { !it.isOffline }

        connectivity.online.value = false

        vm.awaitState("the offline state") { it.isOffline }
        assertTrue(vm.uiState.value.isOffline)
    }

    // ── Pull to refresh ─────────────────────────────────────────────────────

    /**
     * The spinner is held briefly on purpose, so this waits for it to clear
     * rather than assuming it already has.
     */
    @Test
    fun refreshRaisesTheSpinnerAndPutsItBackDown() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)
        vm.awaitState("the group to load") { it.group != null }

        vm.refresh()

        // Awaited rather than asserted outright: refresh launches, so the flag
        // is not set on the caller's thread. The spinner is held for at least
        // REFRESH_SPINNER_MIN_MS, so there is no chance of missing it entirely.
        vm.awaitState("the spinner to rise") { it.isRefreshing }
        vm.awaitState("the spinner to clear") { !it.isRefreshing }
        assertFalse(vm.uiState.value.isRefreshing)
    }

    /** A refresh already running must not be restarted by a second pull. */
    @Test
    fun aSecondPullWhileRefreshingIsIgnored() = runBlocking {
        val vm = viewModel()
        vm.loadGroup(groupId)
        vm.awaitState("the group to load") { it.group != null }

        groups.getGroupCalls = 0
        vm.refresh()
        // The second pull only proves anything once the first has actually
        // raised the flag it is meant to be blocked by.
        vm.awaitState("the spinner to rise") { it.isRefreshing }
        vm.refresh()
        vm.awaitState("the spinner to clear") { !it.isRefreshing }

        assertEquals(1, groups.getGroupCalls)
    }

    // ── Doubles ─────────────────────────────────────────────────────────────

    /** Reports a connection state a test chose, rather than the device's. */
    private class FakeConnectivity(
        context: android.content.Context,
    ) : ConnectivityObserver(context) {
        val online = MutableStateFlow(true)
        override val isOnline: Flow<Boolean> = online
    }

    /** Records what the ViewModel chose to log instead of sending it anywhere. */
    private class RecordingAnalytics : AnalyticsManager {
        val weeklyGoalReached = mutableListOf<String>()

        override fun logWeeklyGoalReached(groupId: String, target: Int) {
            weeklyGoalReached += groupId
        }

        override fun logEvent(eventName: String, params: Map<String, Any>?) = Unit
        override fun logWelcomeScreenViewed() = Unit
        override fun logProfileSetupStarted() = Unit
        override fun logProfileCompleted() = Unit
        override fun logHealthConnectScreenViewed() = Unit
        override fun logHealthConnectPermissionRequested() = Unit
        override fun logHealthConnectPermissionGranted() = Unit
        override fun logHealthConnectPermissionDenied() = Unit
        override fun logHealthConnectSkipped() = Unit
        override fun logHomeScreenReached() = Unit
        override fun logGroupCreated(groupId: String, groupName: String) = Unit
        override fun logGroupJoined(groupId: String) = Unit
        override fun logGroupScreenViewed(groupId: String) = Unit
        override fun logInviteScreenViewed(groupId: String) = Unit
        override fun logInviteLinkUsed(groupId: String) = Unit
        override fun logStepsSyncStarted() = Unit
        override fun logStepsSyncSuccess(durationMs: Long) = Unit
        override fun logStepsSyncFailed(reason: String, durationMs: Long) = Unit
        override fun logStepsSyncSkipped(durationMs: Long) = Unit
    }

    /**
     * Only the four members [GroupViewModel] reaches for are implemented. The
     * rest fail loudly rather than returning something plausible, so a future
     * change that starts calling one is a clear failure instead of a silent
     * pass against a value nobody chose.
     */
    private class FakeGroupRepository : GroupRepository {
        var groupResult: Result<Group> = Result.failure(AppError.NotFound())
        var membershipResult: Result<Membership> = Result.failure(AppError.NotFound())
        var updateTargetResult: Result<Unit> = Result.success(Unit)
        var updatedTarget: Int? = null
        var getGroupCalls = 0

        val memberships = MutableSharedFlow<List<Membership>>(replay = 1)

        /** Set to make the stream fail instead of emitting. */
        var membershipsOverride: Flow<List<Membership>>? = null

        override suspend fun getGroup(groupId: String): Result<Group> {
            getGroupCalls++
            return groupResult
        }

        override fun getGroupMemberships(groupId: String): Flow<List<Membership>> =
            membershipsOverride ?: memberships

        override suspend fun getMembership(userId: String, groupId: String): Result<Membership> =
            membershipResult

        override suspend fun updateWeeklyTarget(groupId: String, target: Int): Result<Unit> =
            updateTargetResult.onSuccess { updatedTarget = target }

        override suspend fun createGroup(group: Group): Result<Group> = notUsed()
        override suspend fun updateGroup(group: Group): Result<Unit> = notUsed()
        override suspend fun getGroupByInviteLink(inviteLink: String): Result<Group> = notUsed()
        override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> = notUsed()
        override fun getGroupMembers(groupId: String): Flow<List<User>> = notUsed()
        override suspend fun publishMyWeeklySteps(
            groupId: String,
            userId: String,
            steps: Long,
            weekStart: String,
            todaySteps: Long,
            todayDate: String,
        ): Result<Unit> = notUsed()

        override fun getUserGroups(userId: String): Flow<List<Group>> = notUsed()
        override suspend fun updateMemberCap(groupId: String, cap: Int): Result<Unit> = notUsed()
        override suspend fun deactivateInviteLink(groupId: String): Result<Unit> = notUsed()
        override suspend fun updateGroupSettings(
            groupId: String,
            name: String,
            weeklyTarget: Int,
            dailyPerPersonTarget: Int,
            maxMemberCap: Int,
            canMembersEditTarget: Boolean,
            weekStartDay: String,
        ): Result<Unit> = notUsed()

        override suspend fun removeMember(groupId: String, userId: String): Result<Unit> = notUsed()
        override suspend fun updateGroupPhoto(groupId: String, photoUrl: String): Result<Unit> =
            notUsed()

        override suspend fun publishWeeklyTotal(
            groupId: String,
            weeklySteps: Long,
            weekStart: String,
        ): Result<Unit> = notUsed()
    }

    private class FakeStepRepository : StepRepository {
        override suspend fun syncTodaySteps(userId: String): Result<Unit> = Result.success(Unit)

        override suspend fun saveStepEntry(stepEntry: StepEntry): Result<Unit> = notUsed()
        override suspend fun getStepsForUser(userId: String, date: String): Result<StepEntry> =
            notUsed()

        override suspend fun getStepsForUserInRange(
            userId: String,
            startDate: String,
            endDate: String,
        ): Flow<List<StepEntry>> = notUsed()
    }

    private class FakeAuthRepository : AuthRepository {
        var currentUser: User? = null

        override suspend fun getCurrentUser(): User? = currentUser

        override suspend fun isUserLoggedIn(): Boolean = notUsed()
        override suspend fun signOut(): Result<Unit> = notUsed()
        override suspend fun deleteAccount(): Result<Unit> = notUsed()
        override suspend fun requiresRecentLogin(): Boolean = notUsed()
        override suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> = notUsed()
        override suspend fun reauthenticateWithEmail(password: String): Result<Unit> = notUsed()
        override suspend fun reauthenticateWithPhone(
            verificationId: String,
            code: String,
        ): Result<Unit> = notUsed()

        override suspend fun getAuthMethods(): AuthMethods = notUsed()
        override suspend fun signUpWithEmail(email: String, password: String): Result<User> =
            notUsed()

        override suspend fun signInWithEmail(email: String, password: String): Result<User> =
            notUsed()

        override suspend fun sendPasswordReset(email: String): Result<Unit> = notUsed()
        override suspend fun updatePassword(newPassword: String): Result<Unit> = notUsed()
        override suspend fun signInWithGoogle(idToken: String): Result<User> = notUsed()
        override suspend fun startPhoneVerification(
            phoneNumber: String,
            activity: Any,
        ): Result<PhoneVerification> = notUsed()

        override suspend fun verifyPhoneCode(verificationId: String, code: String): Result<User> =
            notUsed()

        override suspend fun linkGoogle(idToken: String): Result<User> = notUsed()
        override suspend fun linkEmail(email: String, password: String): Result<User> = notUsed()
        override suspend fun linkPhone(verificationId: String, code: String): Result<User> =
            notUsed()
    }
}

private fun notUsed(): Nothing =
    throw UnsupportedOperationException("GroupViewModel does not use this, so it has no fake")
