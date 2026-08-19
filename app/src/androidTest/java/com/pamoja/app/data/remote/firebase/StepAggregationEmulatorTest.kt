package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.model.WeekWindow
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
import java.util.UUID

/**
 * Weekly step aggregation, and who is allowed to see it.
 *
 * `CHECKLIST.md` §6.3 names this as a data-corruption risk: the combined total
 * is the number the whole product is about.
 *
 * Its shape changed on 2026-08-19. The leaderboard used to query every member's
 * step documents, and that query could not be secured at all: a Firestore list
 * rule is checked against what the query constrains, and it named no group, so
 * no rule could permit it without permitting any signed-in account to read every
 * user's step history. Each member now publishes their own totals onto their own
 * membership and the leaderboard reads those.
 *
 * So these tests come in two halves: the totals still add up, and the steps
 * collection is now private.
 *
 * Requires the emulators, see [FirebaseEmulator].
 */
@RunWith(AndroidJUnit4::class)
class StepAggregationEmulatorTest {

    private val clients = mutableListOf<FirebaseEmulator.Client>()

    private val healthConnectReader by lazy {
        HealthConnectReader(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Before
    fun wipeEmulators() {
        FirebaseEmulator.reset()
    }

    @After
    fun closeClients() {
        clients.forEach { it.close() }
        clients.clear()
    }

    private suspend fun newUser(): FirebaseEmulator.Client =
        FirebaseEmulator.signIn().also { clients += it }

    private fun stepsOf(client: FirebaseEmulator.Client) =
        FirebaseStepRepositoryImpl(client.firestore, healthConnectReader)

    private fun groupsOf(client: FirebaseEmulator.Client) =
        FirebaseGroupRepositoryImpl(client.firestore)

    private suspend fun record(client: FirebaseEmulator.Client, date: LocalDate, steps: Long) {
        stepsOf(client).saveStepEntry(StepEntry(client.uid, steps, date.toString())).getOrThrow()
    }

    private suspend fun createGroup(admin: FirebaseEmulator.Client): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "Aggregation group",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        groupsOf(admin).createGroup(group).getOrThrow()
        return group
    }

    // ── The totals still add up ──────────────────────────────────────────────

    /**
     * A member's own week, which is what each device sums before publishing.
     *
     * The window's edges are inclusive and its neighbours are not. An off-by-one
     * at either end silently moves a day's steps between weeks, which shows up
     * as a total that shrinks overnight for no visible reason.
     */
    @Test
    fun myWeekIncludesItsEdgesAndExcludesTheDaysEitherSide() = runBlocking {
        val user = newUser()
        val today = LocalDate.of(2026, 8, 19)
        val start = LocalDate.parse(WeekWindow.startOf(DayOfWeek.MONDAY, today))
        val end = LocalDate.parse(WeekWindow.endOf(DayOfWeek.MONDAY, today))

        record(user, start.minusDays(1), 1_000)
        record(user, start, 2_000)
        record(user, start.plusDays(3), 3_000)
        record(user, end, 4_000)
        record(user, end.plusDays(1), 5_000)

        val mine = stepsOf(user)
            .getStepsForUserInRange(user.uid, start.toString(), end.toString())
            .first()

        assertEquals(listOf(2_000L, 3_000L, 4_000L), mine.map { it.stepCount }.sorted())
        assertEquals(9_000L, mine.sumOf { it.stepCount })
    }

    /**
     * `CHECKLIST.md` §6.3: "Combined group total equals the sum of member weekly
     * totals", now assembled from what each member published onto their own
     * membership rather than from a query across their step documents.
     */
    @Test
    fun theGroupTotalIsTheSumOfWhatEachMemberPublished() = runBlocking {
        val alice = newUser()
        val bob = newUser()
        val group = createGroup(alice)
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()

        val monday = WeekWindow.startOf(DayOfWeek.MONDAY, LocalDate.of(2026, 8, 19))

        groupsOf(alice).publishMyWeeklySteps(group.groupId, alice.uid, 7_500, monday, 1_200, "2026-08-19").getOrThrow()
        groupsOf(bob).publishMyWeeklySteps(group.groupId, bob.uid, 2_500, monday, 800, "2026-08-19").getOrThrow()

        val memberships = groupsOf(alice).getGroupMemberships(group.groupId).first()

        assertEquals(2, memberships.size)
        assertEquals(7_500L, memberships.first { it.userId == alice.uid }.weeklySteps)
        assertEquals(2_500L, memberships.first { it.userId == bob.uid }.weeklySteps)
        assertEquals(10_000L, memberships.sumOf { it.weeklySteps })
        assertEquals(2_000L, memberships.sumOf { it.todaySteps })
    }

    /** A member who has never synced contributes zero rather than breaking the total. */
    @Test
    fun aMemberWhoNeverSyncedContributesZero() = runBlocking {
        val alice = newUser()
        val silent = newUser()
        val group = createGroup(alice)
        groupsOf(silent).joinGroup(group.groupId, silent.uid).getOrThrow()

        val monday = WeekWindow.startOf(DayOfWeek.MONDAY, LocalDate.of(2026, 8, 19))
        groupsOf(alice).publishMyWeeklySteps(group.groupId, alice.uid, 6_000, monday, 0, "").getOrThrow()

        val memberships = groupsOf(alice).getGroupMemberships(group.groupId).first()

        assertEquals(6_000L, memberships.sumOf { it.weeklySteps })
        assertEquals(0L, memberships.first { it.userId == silent.uid }.weeklySteps)
        assertEquals("", memberships.first { it.userId == silent.uid }.weekStart)
    }

    /** Nobody may publish a figure onto somebody else's membership. */
    @Test
    fun aMemberCannotPublishOntoAnotherMembersRow() = runBlocking {
        val alice = newUser()
        val bob = newUser()
        val group = createGroup(alice)
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()

        val monday = WeekWindow.startOf(DayOfWeek.MONDAY, LocalDate.of(2026, 8, 19))
        val forged = groupsOf(bob)
            .publishMyWeeklySteps(group.groupId, alice.uid, 999_999, monday, 0, "")

        assertTrue("Bob must not be able to write Alice's total, got $forged", forged.isFailure)
    }

    /** The ceiling exists so the leaderboard cannot be written an absurd number. */
    @Test
    fun anImplausibleTotalIsRefused() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val monday = WeekWindow.startOf(DayOfWeek.MONDAY, LocalDate.of(2026, 8, 19))

        val absurd = runCatching {
            alice.firestore.collection("memberships")
                .document("${alice.uid}_${group.groupId}")
                .update(mapOf("weeklySteps" to 50_000_000L, "weekStart" to monday))
                .await()
        }

        assertTrue("A 50 million step week must be refused, got $absurd", absurd.isFailure)
    }

    // ── The steps collection is private ──────────────────────────────────────

    /** The exposure this restructuring exists to close. */
    @Test
    fun aStrangerCannotEnumerateEveryUsersSteps() = runBlocking {
        val alice = newUser()
        record(alice, LocalDate.of(2026, 8, 19), 12_345)

        val stranger = newUser()
        val dump = runCatching {
            stranger.firestore.collection("steps").get().await().size()
        }

        assertTrue(
            "A signed-in account must not be able to read the steps collection, got $dump",
            dump.isFailure,
        )
    }

    /** Nor a group-mate's, which is what the old leaderboard query relied on. */
    @Test
    fun aGroupMateCannotReadYourStepDocuments() = runBlocking {
        val alice = newUser()
        val bob = newUser()
        val group = createGroup(alice)
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        record(alice, LocalDate.of(2026, 8, 19), 9_000)

        val peek = runCatching {
            bob.firestore.collection("steps")
                .whereEqualTo("userId", alice.uid)
                .get().await().size()
        }

        assertTrue(
            "Sharing a group must not grant access to raw step history, got $peek",
            peek.isFailure,
        )
    }

    /** And the owner can still read their own, which export and deletion need. */
    @Test
    fun youCanStillReadYourOwnSteps() = runBlocking {
        val user = newUser()
        record(user, LocalDate.of(2026, 8, 17), 3_000)
        record(user, LocalDate.of(2026, 8, 18), 4_000)

        val mine = stepsOf(user)
            .getStepsForUserInRange(user.uid, "2026-08-17", "2026-08-18")
            .first()

        assertEquals(7_000L, mine.sumOf { it.stepCount })
    }
}
