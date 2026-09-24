package com.pamoja.app.ui.weeklyreview

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupAccess
import com.pamoja.app.domain.model.GroupAccessSnapshot
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupFeature
import com.pamoja.app.domain.model.GroupPreview
import com.pamoja.app.domain.model.GroupWeekContribution
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.NextWeekPlan
import com.pamoja.app.domain.model.NextWeekPlanChoice
import com.pamoja.app.domain.model.NextWeekPlanResponse
import com.pamoja.app.domain.model.NextWeekPlanStatus
import com.pamoja.app.domain.model.NextWeekResponse
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.AuthMethods
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.GroupAccessRepository
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.domain.repository.GroupWeekRepository
import com.pamoja.app.domain.repository.NextWeekPlanRepository
import com.pamoja.app.domain.repository.PhoneVerification
import com.pamoja.app.domain.repository.PhoneVerificationPurpose
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.ObserveGroupAccessUseCase
import com.pamoja.app.domain.usecase.ObserveGroupWeekSummariesUseCase
import com.pamoja.app.domain.usecase.ObserveNextWeekPlanUseCase
import com.pamoja.app.domain.usecase.ObserveMyNextWeekResponseUseCase
import com.pamoja.app.domain.usecase.SaveNextWeekPlanUseCase
import com.pamoja.app.domain.usecase.SaveNextWeekResponseUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WeeklyReviewViewModelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val groupId = "6413795d-42d0-4f2b-80df-f017a9d32817"
    private val userId = "member-a"
    private val group = Group(
        groupId = groupId,
        name = "Morning walkers",
        adminId = "member-a",
        weeklyTarget = 70_000,
        memberCount = 2,
    )

    private lateinit var groups: FakeGroupRepository
    private lateinit var weeks: FakeGroupWeekRepository
    private lateinit var connectivity: FakeConnectivity
    private lateinit var access: FakeGroupAccessRepository
    private lateinit var auth: FakeAuthRepository
    private lateinit var nextWeekPlans: FakeNextWeekPlanRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
        groups = FakeGroupRepository(group)
        weeks = FakeGroupWeekRepository()
        connectivity = FakeConnectivity(context)
        access = FakeGroupAccessRepository()
        auth = FakeAuthRepository(User(userId = userId, name = "Asha"))
        nextWeekPlans = FakeNextWeekPlanRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun completedWeeksAndCurrentMemberNamesBuildTheReview() = runBlocking {
        groups.memberships.value = listOf(
            Membership(
                userId = "member-a",
                groupId = groupId,
                displayName = "Asha",
                photoUrl = "",
            ),
        )
        weeks.values.value = listOf(
            summary("2026-08-17", 84_000L, goal = 70_000L),
            summary("2026-08-10", 60_000L, goal = 70_000L),
        )

        val state = viewModel().awaitState("completed weeks") {
            it.historyLoaded && it.currentMembers.isNotEmpty()
        }

        assertEquals(2, state.weeks.size)
        assertEquals(1, state.hitCount)
        assertEquals(1, state.missedCount)
        assertEquals(84_000L, state.bestWeek?.totalSteps)
        assertEquals("Asha", state.currentMembers["member-a"]?.displayName)
        assertFalse(state.isEmpty)
    }

    @Test
    fun membershipDisplayFailureDoesNotHideGroupHistory() = runBlocking {
        groups.membershipsOverride = flow { throw AppError.Offline() }
        weeks.values.value = listOf(summary("2026-08-17", 84_000L, 70_000L))

        val state = viewModel().awaitState("history without display names") {
            it.historyLoaded
        }

        assertEquals(1, state.weeks.size)
        assertTrue(state.currentMembers.isEmpty())
        assertEquals(null, state.loadError)
    }

    @Test
    fun failedHistoryCanRetryWithoutLosingTheGroup() = runBlocking {
        weeks.override = flow { throw AppError.Network() }
        val viewModel = viewModel()
        val failed = viewModel.awaitState("history failure") { it.loadError != null }
        assertTrue(failed.loadError is AppError.Network)
        assertEquals(group, failed.group)

        weeks.override = MutableStateFlow(listOf(summary("2026-08-17", 70_000L, 70_000L)))
        viewModel.retry()
        val recovered = viewModel.awaitState("history recovery") { it.historyLoaded }

        assertEquals(1, recovered.weeks.size)
        assertEquals(null, recovered.loadError)
    }

    @Test
    fun removedMemberGetsATerminalStateInsteadOfAnEndlessRetry() = runBlocking {
        groups.groupResult = Result.failure(AppError.PermissionDenied())

        val state = viewModel().awaitState("unavailable group") {
            it.isGroupUnavailable
        }

        assertFalse(state.isLoading)
        assertTrue(state.loadError is AppError.PermissionDenied)
    }

    @Test
    fun historyPermissionLossClearsPreviouslyLoadedReview() = runBlocking {
        groups.memberships.value = listOf(
            Membership(
                userId = "member-a",
                groupId = groupId,
                displayName = "Asha",
            ),
        )
        weeks.override = flow {
            emit(listOf(summary("2026-08-17", 84_000L, 70_000L)))
            throw AppError.PermissionDenied()
        }

        val state = viewModel().awaitState("terminal history permission loss") {
            it.isGroupUnavailable
        }

        assertFalse(state.isLoading)
        assertFalse(state.historyLoaded)
        assertTrue(state.weeks.isEmpty())
        assertTrue(state.currentMembers.isEmpty())
        assertEquals(null, state.group)
        assertTrue(state.loadError is AppError.PermissionDenied)
    }

    @Test
    fun dayOneFreeGroupDoesNotQueryRestrictedHistoryOrDisappear() = runBlocking {
        var historyRead = false
        weeks.override = flow {
            historyRead = true
            throw AppError.PermissionDenied()
        }
        val state = viewModel(dayOneEnabled = true).awaitState("free day-one group") {
            it.groupAccess == GroupAccessState.Free && !it.isLoading
        }
        assertFalse(historyRead)
        assertFalse(state.isGroupUnavailable)
        assertEquals(group, state.group)
        assertFalse(state.canReadCircleHistory)
        assertEquals(null, state.loadError)
    }

    @Test
    fun previewGateDoesNotReadHistoryBeforeTheSharedPreviewStarts() = runBlocking {
        weeks.values.value = listOf(summary("2026-08-17", 84_000L, 70_000L))

        val state = viewModel(previewEnabled = true).awaitState("free preview state") {
            it.groupAccess == GroupAccessState.Free
        }

        assertFalse(state.isLoading)
        assertFalse(state.historyLoaded)
        assertTrue(state.weeks.isEmpty())
    }

    @Test
    fun activeSharedPreviewLoadsWeeklyHistory() = runBlocking {
        val preview = preview(validUntilMillis = System.currentTimeMillis() + 60_000L)
        access.values.value = Result.success(GroupAccessSnapshot(preview = preview))
        weeks.values.value = listOf(summary("2026-08-17", 84_000L, 70_000L))

        val state = viewModel(previewEnabled = true).awaitState("preview history") {
            it.groupAccess is GroupAccessState.Preview && it.historyLoaded
        }

        assertEquals(listOf("2026-08-17"), state.weeks.map { it.weekStart })
        assertTrue(state.canReadCircleHistory)
    }

    @Test
    fun existingPremiumLeaseAutomaticallyLoadsNextWeekPlanning() = runBlocking {
        val now = System.currentTimeMillis()
        access.values.value = Result.success(
            GroupAccessSnapshot(
                premiumAccess = GroupAccess(
                    groupId = groupId,
                    // Represents a lease created before Next Week Together.
                    featureSet = setOf(GroupFeature.CircleV1),
                    validUntilMillis = now + 60_000L,
                ),
            ),
        )
        nextWeekPlans.plan.value = Result.success(
            NextWeekPlan(
                groupId = groupId,
                weekStart = "2026-09-14",
                targetSteps = 70_000,
                choice = NextWeekPlanChoice.Repeat,
                sourceWeekStart = "2026-09-07",
                basisTotalSteps = 58_000L,
                basisTargetSteps = 70_000L,
                status = NextWeekPlanStatus.Scheduled,
                createdBy = userId,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )

        val state = viewModel(previewEnabled = true).awaitState("premium planning") {
            it.groupAccess is GroupAccessState.Premium && it.nextWeekPlanLoaded
        }

        assertEquals(70_000, state.nextWeekPlan?.targetSteps)
        assertTrue(state.groupAccess.hasNextWeekTogether)
    }

    @Test
    fun endedPreviewClearsHistoryWithoutMarkingTheGroupUnavailable() = runBlocking {
        access.values.value = Result.success(
            GroupAccessSnapshot(
                preview = preview(validUntilMillis = System.currentTimeMillis() + 60_000L),
            ),
        )
        weeks.values.value = listOf(summary("2026-08-17", 84_000L, 70_000L))
        val viewModel = viewModel(previewEnabled = true)
        viewModel.awaitState("loaded preview") { it.historyLoaded }

        val ended = preview(validUntilMillis = System.currentTimeMillis() - 1L)
        access.values.value = Result.success(GroupAccessSnapshot(preview = ended))
        val state = viewModel.awaitState("ended preview") {
            it.groupAccess is GroupAccessState.PreviewExpired
        }

        assertEquals(group, state.group)
        assertFalse(state.isGroupUnavailable)
        assertFalse(state.historyLoaded)
        assertTrue(state.weeks.isEmpty())
    }

    @Test
    fun dayOnePlanningWorksIndependentlyOfHistoryPreviewFlag() = runBlocking {
        access.values.value = Result.success(GroupAccessSnapshot(premiumAccess = GroupAccess(
            groupId, setOf(GroupFeature.CircleV1), System.currentTimeMillis() + 60_000L,
        )))
        val model = viewModel(dayOneEnabled = true)
        model.awaitState("day-one paid plan") { it.nextWeekPlanLoaded && it.groupAccess.isPremium }
        model.saveNextWeekPlan(NextWeekPlanChoice.Repeat)
        val state = model.awaitState("day-one saved") { nextWeekPlans.savedTarget == 70_000 }
        assertEquals(70_000, state.group?.weeklyTarget)
        assertEquals(null, nextWeekPlans.savedSource)
        assertTrue(state.weeks.isEmpty())
    }

    @Test
    fun retainedScreenDoesNotPollCalendarWithoutAViewer() = runBlocking {
        access.values.value = Result.success(GroupAccessSnapshot(premiumAccess = GroupAccess(
            groupId, setOf(GroupFeature.CircleV1), System.currentTimeMillis() + 60_000L,
        )))
        nextWeekPlans.window = nextWeekPlans.window.copy(remainingMillis = 250)
        val model = viewModel(dayOneEnabled = true)
        delay(350)
        assertEquals(0, nextWeekPlans.windowRequests)
        model.awaitState("visible calendar") { it.nextWeekPlanLoaded }
        val observedRequests = nextWeekPlans.windowRequests
        delay(600)
        assertEquals(observedRequests, nextWeekPlans.windowRequests)
    }

    @Test
    fun openPlanningScreenMovesToServerWeekAndKeepsPromiseAfterExpiry() = runBlocking {
        access.values.value = Result.success(GroupAccessSnapshot(premiumAccess = GroupAccess(
            groupId, setOf(GroupFeature.CircleV1), System.currentTimeMillis() + 60_000L,
        )))
        nextWeekPlans.window = nextWeekPlans.window.copy(remainingMillis = 250)
        nextWeekPlans.summary.value = Result.success(com.pamoja.app.domain.model.ScheduledGoal(
            "2026-09-14", 60_000, "Asia/Kolkata", "scheduled"))
        val model = viewModel(previewEnabled = true, dayOneEnabled = true)
        model.awaitState("initial server week") { it.nextWeekPlanLoaded && it.scheduledGoal != null }
        nextWeekPlans.window = nextWeekPlans.window.copy(weekStart = "2026-09-21", currentTargetSteps = 60_000)
        model.awaitState("server boundary") { it.nextWeekStart == "2026-09-21" && it.group?.weeklyTarget == 60_000 }
        access.values.value = Result.success(GroupAccessSnapshot())
        val expired = model.awaitState("read-only promise") { !it.groupAccess.hasNextWeekTogether && it.nextWeekPlan == null }
        assertEquals(60_000, expired.scheduledGoal?.targetSteps)
        model.saveNextWeekPlan(NextWeekPlanChoice.Repeat)
        assertEquals(null, nextWeekPlans.savedTarget)
    }

    private fun viewModel(previewEnabled: Boolean = false, dayOneEnabled: Boolean = false) = WeeklyReviewViewModel(
        savedStateHandle = SavedStateHandle(mapOf("groupId" to groupId)),
        getGroupUseCase = GetGroupUseCase(groups),
        observeGroupWeekSummariesUseCase = ObserveGroupWeekSummariesUseCase(weeks),
        getGroupMembershipsUseCase = GetGroupMembershipsUseCase(groups),
        getCurrentUserUseCase = GetCurrentUserUseCase(auth),
        observeGroupAccessUseCase = ObserveGroupAccessUseCase(access, Clock.systemUTC()),
        observeNextWeekPlanUseCase = ObserveNextWeekPlanUseCase(nextWeekPlans),
        observeMyNextWeekResponseUseCase = ObserveMyNextWeekResponseUseCase(nextWeekPlans),
        saveNextWeekPlanUseCase = SaveNextWeekPlanUseCase(nextWeekPlans),
        saveNextWeekResponseUseCase = SaveNextWeekResponseUseCase(nextWeekPlans),
        connectivityObserver = connectivity,
        clock = Clock.systemUTC(),
        circlePreviewEnabled = previewEnabled,
        dayOnePlanningEnabled = dayOneEnabled,
    )

    private fun preview(validUntilMillis: Long) = GroupPreview(
        groupId = groupId,
        featureSet = setOf(GroupFeature.CircleV1),
        eligibleWeekStart = "2026-08-17",
        startedAtMillis = System.currentTimeMillis() - 1_000L,
        validUntilMillis = validUntilMillis,
    )

    private fun summary(weekStart: String, total: Long, goal: Long) = GroupWeekSummary(
        groupId = groupId,
        weekStart = weekStart,
        weekEnd = java.time.LocalDate.parse(weekStart).plusDays(6).toString(),
        targetSteps = goal,
        totalSteps = total,
        memberCount = 2,
        activeMemberCount = 2,
        contributions = listOf(GroupWeekContribution("member-a", total)),
        finalizedAtEpochMillis = 1L,
    )

    private suspend fun WeeklyReviewViewModel.awaitState(
        description: String,
        predicate: (WeeklyReviewUiState) -> Boolean,
    ): WeeklyReviewUiState {
        // A real screen subscribes through collectAsStateWithLifecycle. Polling
        // .value would never activate the calendar's while-observed refresh.
        withTimeoutOrNull(5_000L) { uiState.first(predicate) }?.let { return it }
        fail("Timed out waiting for $description. Last state was ${uiState.value}")
        error("unreachable")
    }

    private class FakeGroupWeekRepository : GroupWeekRepository {
        val values = MutableStateFlow<List<GroupWeekSummary>>(emptyList())
        var override: Flow<List<GroupWeekSummary>>? = null

        override fun observeCompletedWeeks(groupId: String): Flow<List<GroupWeekSummary>> =
            override ?: values
    }

    private class FakeGroupAccessRepository : GroupAccessRepository {
        val values = MutableStateFlow<Result<GroupAccessSnapshot>>(
            Result.success(GroupAccessSnapshot()),
        )

        override fun observeForCurrentMember(
            groupId: String,
            userId: String,
        ): Flow<Result<GroupAccessSnapshot>> = values
    }

    private class FakeNextWeekPlanRepository : NextWeekPlanRepository {
        @Volatile var window = com.pamoja.app.domain.model.PlanningWindow("2026-09-14", "Asia/Kolkata", 60_000, 70_000, "MONDAY")
        @Volatile var windowRequests = 0
        val summary = MutableStateFlow<Result<com.pamoja.app.domain.model.ScheduledGoal?>>(Result.success(null))
        override fun observeScheduledGoal(groupId: String) = summary
        override suspend fun planningWindow(groupId: String, timeZone: String): Result<com.pamoja.app.domain.model.PlanningWindow> {
            windowRequests += 1
            return Result.success(window)
        }
        override suspend fun saveScheduledPlan(groupId: String, weekStart: String, targetSteps: Int,
            choice: NextWeekPlanChoice, sourceWeekStart: String?, basisTargetSteps: Int): Result<Unit> {
            savedTarget = targetSteps
            return Result.success(Unit)
        }
        @Volatile var savedTarget: Int? = null
        @Volatile var savedSource: GroupWeekSummary? = null
        val plan = MutableStateFlow<Result<NextWeekPlan?>>(Result.success(null))
        val response = MutableStateFlow<Result<NextWeekPlanResponse?>>(Result.success(null))

        override fun observePlan(groupId: String, weekStart: String) = plan

        override fun observeMyResponse(groupId: String, weekStart: String, userId: String) =
            response

        override suspend fun savePlan(
            groupId: String,
            weekStart: String,
            targetSteps: Int,
            choice: NextWeekPlanChoice,
            source: GroupWeekSummary?,
            userId: String,
        ): Result<Unit> {
            savedSource = source
            savedTarget = targetSteps
            return Result.success(Unit)
        }

        override suspend fun saveResponse(
            groupId: String,
            weekStart: String,
            userId: String,
            response: NextWeekResponse,
        ): Result<Unit> = Result.success(Unit)
    }

    private class FakeAuthRepository(
        var currentUser: User?,
    ) : AuthRepository {
        override suspend fun getCurrentUser(): User? = currentUser
        override suspend fun isUserLoggedIn(): Boolean = currentUser != null
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
            purpose: PhoneVerificationPurpose,
        ): Result<PhoneVerification> = notUsed()
        override suspend fun verifyPhoneCode(
            verificationId: String,
            code: String,
        ): Result<User> = notUsed()
        override suspend fun linkGoogle(idToken: String): Result<User> = notUsed()
        override suspend fun linkEmail(email: String, password: String): Result<User> = notUsed()
        override suspend fun linkPhone(verificationId: String, code: String): Result<User> =
            notUsed()
    }

    private class FakeConnectivity(context: android.content.Context) :
        ConnectivityObserver(context) {
        override val isOnline: Flow<Boolean> = MutableStateFlow(true)
    }

    private class FakeGroupRepository(group: Group) : GroupRepository {
        var groupResult: Result<Group> = Result.success(group)
        val memberships = MutableStateFlow<List<Membership>>(emptyList())
        var membershipsOverride: Flow<List<Membership>>? = null

        override suspend fun getGroup(groupId: String): Result<Group> = groupResult
        override fun getGroupMemberships(groupId: String): Flow<List<Membership>> =
            membershipsOverride ?: memberships

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

        override suspend fun getMembership(userId: String, groupId: String): Result<Membership> =
            notUsed()

        override fun getUserGroups(userId: String): Flow<List<Group>> = notUsed()
        override suspend fun updateMemberCap(groupId: String, cap: Int): Result<Unit> = notUsed()
        override suspend fun updateWeeklyTarget(groupId: String, target: Int): Result<Unit> = notUsed()
        override suspend fun deactivateInviteLink(groupId: String): Result<Unit> = notUsed()
        override suspend fun updateGroupSettings(
            groupId: String,
            name: String,
            weeklyTarget: Int,
            maxMemberCap: Int,
            canMembersEditTarget: Boolean,
            weekStartDay: String,
        ): Result<Unit> = notUsed()

        override suspend fun removeMember(groupId: String, userId: String): Result<Unit> = notUsed()
        override suspend fun deleteGroup(groupId: String): Result<Unit> = notUsed()
        override suspend fun updateGroupPhoto(groupId: String, photoUrl: String): Result<Unit> =
            notUsed()

        override suspend fun publishWeeklyTotal(
            groupId: String,
            weeklySteps: Long,
            weekStart: String,
        ): Result<Unit> = notUsed()
    }
}

private fun <T> notUsed(): T = error("Not used in this test")
