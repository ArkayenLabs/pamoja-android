package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.model.WeekWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Weekly step aggregation, exercised against a real Firestore emulator.
 *
 * `CHECKLIST.md` §6.3 names this as a data-corruption risk: the combined group
 * total is the number the whole product is about, and it is assembled from a
 * compound query (`whereIn` on member ids, plus a range over `date`) whose
 * boundaries are easy to get wrong and impossible to eyeball.
 *
 * The window itself comes from [WeekWindow], which has its own unit tests.
 * What is checked here is that the query built from it selects exactly the
 * right documents out of a database that also contains the wrong ones.
 *
 * Requires the emulators, see [FirebaseEmulator].
 */
@RunWith(AndroidJUnit4::class)
class StepAggregationEmulatorTest {

    private val clients = mutableListOf<FirebaseEmulator.Client>()

    /** Real reader, never used by the paths under test, but the constructor wants one. */
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

    private fun repositoryFor(client: FirebaseEmulator.Client) =
        FirebaseStepRepositoryImpl(client.firestore, healthConnectReader)

    /** Each user writes their own steps, which is the only thing the rules allow. */
    private suspend fun record(client: FirebaseEmulator.Client, date: LocalDate, steps: Long) {
        repositoryFor(client)
            .saveStepEntry(StepEntry(client.uid, steps, date.toString()))
            .getOrThrow()
    }

    private suspend fun weekEntries(
        reader: FirebaseEmulator.Client,
        memberIds: List<String>,
        startDay: DayOfWeek,
        today: LocalDate,
    ): List<StepEntry> =
        repositoryFor(reader)
            .getGroupStepsForWeek(
                memberIds,
                WeekWindow.startOf(startDay, today),
                WeekWindow.endOf(startDay, today),
            )
            .first()

    /**
     * The week's edges are inclusive and its neighbours are not.
     *
     * An off-by-one at either end silently moves a day's steps between weeks,
     * which shows up as a total that shrinks overnight for no visible reason.
     */
    @Test
    fun theWindowIncludesItsEdgesAndExcludesTheDaysEitherSide() = runBlocking {
        val user = newUser()
        val today = LocalDate.of(2026, 8, 19)
        val start = LocalDate.parse(WeekWindow.startOf(DayOfWeek.MONDAY, today))
        val end = LocalDate.parse(WeekWindow.endOf(DayOfWeek.MONDAY, today))

        record(user, start.minusDays(1), 1_000)
        record(user, start, 2_000)
        record(user, start.plusDays(3), 3_000)
        record(user, end, 4_000)
        record(user, end.plusDays(1), 5_000)

        val entries = weekEntries(user, listOf(user.uid), DayOfWeek.MONDAY, today)

        assertEquals(
            "Only the seven days of the window belong to it",
            listOf(2_000L, 3_000L, 4_000L),
            entries.map { it.stepCount }.sorted(),
        )
        assertEquals(9_000L, entries.sumOf { it.stepCount })
    }

    /**
     * A different week start selects a different set of days from the same data.
     *
     * The week start is a group setting, so this is what stops one group's
     * boundary leaking into another's total.
     */
    @Test
    fun theWeekStartDayDecidesWhichDaysCount() = runBlocking {
        val user = newUser()
        val today = LocalDate.of(2026, 8, 19)
        val mondayStart = LocalDate.parse(WeekWindow.startOf(DayOfWeek.MONDAY, today))

        // The Sunday immediately before the Monday-start week. In a Sunday-start
        // week it is day one; in a Monday-start week it belongs to the week before.
        record(user, mondayStart.minusDays(1), 8_000)
        record(user, mondayStart, 1_000)

        val mondayWeek = weekEntries(user, listOf(user.uid), DayOfWeek.MONDAY, today)
        val sundayWeek = weekEntries(user, listOf(user.uid), DayOfWeek.SUNDAY, today)

        assertEquals(1_000L, mondayWeek.sumOf { it.stepCount })
        assertEquals(9_000L, sundayWeek.sumOf { it.stepCount })
    }

    /**
     * `CHECKLIST.md` §6.3: "Combined group total equals the sum of member
     * weekly totals", and a non-member's steps are not swept in.
     */
    @Test
    fun theGroupTotalIsTheSumOfItsMembersAndNobodyElse() = runBlocking {
        val today = LocalDate.of(2026, 8, 19)
        val monday = LocalDate.parse(WeekWindow.startOf(DayOfWeek.MONDAY, today))

        val alice = newUser()
        val bob = newUser()
        val stranger = newUser()

        record(alice, monday, 3_000)
        record(alice, monday.plusDays(2), 4_500)
        record(bob, monday.plusDays(1), 2_500)
        // Large but inside the 300k anti-cheat ceiling the rules impose, so
        // this is excluded by not being a member rather than by being rejected.
        record(stranger, monday, 250_000)

        val members = listOf(alice.uid, bob.uid)
        val entries = weekEntries(alice, members, DayOfWeek.MONDAY, today)

        val perMember = members.associateWith { id ->
            entries.filter { it.userId == id }.sumOf { it.stepCount }
        }
        assertEquals(7_500L, perMember[alice.uid])
        assertEquals(2_500L, perMember[bob.uid])
        assertEquals(
            "The combined total must be exactly the sum of the member totals",
            perMember.values.sum(),
            entries.sumOf { it.stepCount },
        )
        assertEquals(10_000L, entries.sumOf { it.stepCount })
    }

    /**
     * A member who has never synced contributes zero rather than breaking the
     * total or vanishing from it. `CHECKLIST.md` §6.3 asks for 0, not blank.
     */
    @Test
    fun aMemberWithNoEntriesContributesZero() = runBlocking {
        val today = LocalDate.of(2026, 8, 19)
        val monday = LocalDate.parse(WeekWindow.startOf(DayOfWeek.MONDAY, today))

        val active = newUser()
        val silent = newUser()
        record(active, monday, 6_000)

        val entries = weekEntries(active, listOf(active.uid, silent.uid), DayOfWeek.MONDAY, today)

        assertEquals(6_000L, entries.sumOf { it.stepCount })
        assertEquals(0L, entries.filter { it.userId == silent.uid }.sumOf { it.stepCount })
    }
}
