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
import com.pamoja.app.domain.model.GroupWeekContribution
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.AuthMethods
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.GroupAccessRepository
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.domain.repository.GroupWeekRepository
import com.pamoja.app.domain.repository.PhoneVerification
import com.pamoja.app.domain.repository.PhoneVerificationPurpose
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.ObserveGroupAccessUseCase
import com.pamoja.app.domain.usecase.ObserveGroupWeekSummariesUseCase
import com.pamoja.app.domain.usecase.ObserveLastFinishedWeekUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/**
 * The rebuilt review (Phase 6): the last finished week is free, every earlier
 * one is Premium, and each read owns only its own part of the screen.
 *
 * Instrumented only because ConnectivityObserver needs a Context. The
 * ViewModel writes state with update {}, so Dispatchers.Default as Main is
 * safe here (see BACKLOG.md 3.4 for why that matters elsewhere).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WeeklyReviewViewModelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val groupId = "6413795d-42d0-4f2b-80df-f017a9d32817"
    private val userId = "member-a"
    private val group = Group(
        groupId = groupId,
        name = "Morning walkers",
        adminId = userId,
        weeklyTarget = 70_000,
        memberCount = 2,
        streakThroughWeek = "2026-09-21",
    )

    private lateinit var groups: FakeGroupRepository
    private lateinit var weeks: FakeGroupWeekRepository
    private lateinit var access: FakeGroupAccessRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
        groups = FakeGroupRepository(group)
        weeks = FakeGroupWeekRepository()
        access = FakeGroupAccessRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aFreeMemberGetsLastWeekAndNoHistory() = runBlocking {
        weeks.single["2026-09-21"] = summary("2026-09-21", 84_000)
        weeks.all.value = listOf(summary("2026-09-21", 84_000), summary("2026-09-14", 40_000))
        groups.memberships.value = listOf(Membership(userId = userId, groupId = groupId, displayName = "Asha"))

        val state = viewModel().awaitState("last week") {
            it.lastWeek != null && it.access == GroupAccessState.Free && it.members.isNotEmpty()
        }

        assertEquals(84_000L, state.lastWeek?.totalSteps)
        assertTrue("A free member must not receive the list", state.history.isEmpty())
        assertEquals("Asha", state.members[userId]?.displayName)
        assertTrue("The list must never be asked for without access", weeks.listRequests == 0)
    }

    @Test
    fun premiumAddsEveryEarlierWeekAndAWeekCanBeOpened() = runBlocking {
        weeks.single["2026-09-21"] = summary("2026-09-21", 84_000)
        weeks.all.value = listOf(summary("2026-09-21", 84_000), summary("2026-09-14", 40_000))
        access.values.value = Result.success(
            GroupAccessSnapshot(premiumAccess = GroupAccess(groupId, setOf(GroupFeature.CircleV1), Long.MAX_VALUE)),
        )
        val model = viewModel()

        val state = model.awaitState("history") { it.history.size == 2 && it.lastWeek != null }
        assertEquals(listOf("2026-09-14"), state.earlierWeeks.map { it.weekStart })

        model.selectWeek("2026-09-14")
        assertEquals("2026-09-14", model.awaitState("selection") { it.selectedWeekStart != null }.selectedWeek?.weekStart)
        model.selectWeek("2026-09-21")
        assertNull(model.awaitState("back to last") { it.selectedWeekStart == null }.selectedWeekStart)
    }

    @Test
    fun aGroupWithNoFinishedWeekLoadsToNothingRatherThanForever() = runBlocking {
        groups.groupResult = Result.success(group.copy(streakThroughWeek = ""))

        val state = viewModel().awaitState("loaded") { it.lastWeekLoaded }

        assertNull(state.lastWeek)
    }

    @Test
    fun namesFailingDoesNotHideTheWeek() = runBlocking {
        weeks.single["2026-09-21"] = summary("2026-09-21", 84_000)
        groups.membershipsOverride = flow { throw AppError.Network() }

        val state = viewModel().awaitState("week without names") { it.lastWeek != null }

        assertTrue(state.members.isEmpty())
        assertEquals(null, state.loadError)
    }

    @Test
    fun aRemovedMemberGetsATerminalStateInsteadOfAnEndlessRetry() = runBlocking {
        groups.groupResult = Result.failure(AppError.PermissionDenied())

        val state = viewModel().awaitState("unavailable") { it.isGroupUnavailable }

        assertTrue(state.loadError is AppError.PermissionDenied)
    }

    private fun viewModel() = WeeklyReviewViewModel(
        savedStateHandle = SavedStateHandle(mapOf("groupId" to groupId)),
        getGroupUseCase = GetGroupUseCase(groups),
        observeLastFinishedWeekUseCase = ObserveLastFinishedWeekUseCase(weeks),
        observeGroupWeekSummariesUseCase = ObserveGroupWeekSummariesUseCase(weeks),
        getGroupMembershipsUseCase = GetGroupMembershipsUseCase(groups),
        getCurrentUserUseCase = GetCurrentUserUseCase(FakeAuthRepository(User(userId = userId, name = "Asha"))),
        observeGroupAccessUseCase = ObserveGroupAccessUseCase(access, Clock.systemUTC()),
        connectivityObserver = FakeConnectivity(context),
    )

    private fun summary(weekStart: String, total: Long) = GroupWeekSummary(
        groupId = groupId,
        weekStart = weekStart,
        weekEnd = java.time.LocalDate.parse(weekStart).plusDays(6).toString(),
        targetSteps = 70_000,
        totalSteps = total,
        memberCount = 2,
        activeMemberCount = 2,
        contributions = listOf(GroupWeekContribution(userId, total)),
        finalizedAtEpochMillis = 1L,
    )

    private suspend fun WeeklyReviewViewModel.awaitState(
        description: String,
        predicate: (WeeklyReviewUiState) -> Boolean,
    ): WeeklyReviewUiState {
        withTimeoutOrNull(5_000L) { uiState.first(predicate) }?.let { return it }
        fail("Timed out waiting for $description. Last state was ${uiState.value}")
        error("unreachable")
    }

    private class FakeGroupWeekRepository : GroupWeekRepository {
        val all = MutableStateFlow<List<GroupWeekSummary>>(emptyList())
        val single = mutableMapOf<String, GroupWeekSummary>()
        @Volatile var listRequests = 0

        override fun observeCompletedWeeks(groupId: String): Flow<List<GroupWeekSummary>> {
            listRequests += 1
            return all
        }

        override fun observeWeek(groupId: String, weekStart: String): Flow<GroupWeekSummary?> =
            all.map { single[weekStart] }
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
            walkedDays: Int,
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
