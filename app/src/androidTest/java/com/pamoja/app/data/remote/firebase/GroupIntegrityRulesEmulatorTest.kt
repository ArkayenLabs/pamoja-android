package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FieldValue
import com.pamoja.app.domain.model.Group
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/**
 * What members may write onto the group document.
 *
 * Pins the August 2026 review's H1 (the count driven down by a member who is
 * not leaving) and M4 (an unbounded group step cache), never tested until
 * now, and the October 2026 review's: the invite switched off by a joiner or
 * a leaver, the week marker moved into the future or the distant past (the
 * weekly finalizer closes whatever week it moves away from), an unvalidated
 * start day, and an open key set on create.
 *
 * In pairs: each refusal sits beside the real write it must not break.
 *
 * Requires the emulators, see [FirebaseEmulator].
 */
@RunWith(AndroidJUnit4::class)
class GroupIntegrityRulesEmulatorTest {

    private val clients = mutableListOf<FirebaseEmulator.Client>()

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

    private fun groupsOf(client: FirebaseEmulator.Client) = FirebaseGroupRepositoryImpl(client.firestore)

    private fun newGroup(admin: FirebaseEmulator.Client, cap: Int = 10) = Group(
        groupId = UUID.randomUUID().toString(),
        name = "Integrity group",
        adminId = admin.uid,
        weeklyTarget = 70_000,
        maxMemberCap = cap,
        memberCount = 1,
        createdAt = System.currentTimeMillis(),
        weekStartDay = "MONDAY",
    )

    private suspend fun createGroup(admin: FirebaseEmulator.Client, cap: Int = 10): Group =
        newGroup(admin, cap).also { groupsOf(admin).createGroup(it).getOrThrow() }

    private fun groupRef(client: FirebaseEmulator.Client, groupId: String) =
        client.firestore.collection("groups").document(groupId)

    private fun membershipRef(client: FirebaseEmulator.Client, userId: String, groupId: String) =
        client.firestore.collection("memberships").document("${userId}_$groupId")

    private suspend fun field(client: FirebaseEmulator.Client, groupId: String, name: String): Any? =
        groupRef(client, groupId).get().await().get(name)

    private suspend fun pair(cap: Int = 10): Triple<Group, FirebaseEmulator.Client, FirebaseEmulator.Client> {
        val alice = newUser()
        val group = createGroup(alice, cap)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        return Triple(group, alice, bob)
    }

    // ── The member count ─────────────────────────────────────────────────────

    /** H1: a member who stays cannot walk the count down. */
    @Test
    fun aMemberCannotDriveTheCountDownWithoutLeaving() = runBlocking {
        val (group, alice, bob) = pair()

        val drop = runCatching { groupRef(bob, group.groupId).update("memberCount", 1).await() }
        assertTrue("A decrement with the membership kept must be refused, got $drop", drop.isFailure)
        assertEquals(2L, field(alice, group.groupId, "memberCount"))

        // The pair: actually leaving.
        groupsOf(bob).removeMember(group.groupId, bob.uid, keepOut = false).getOrThrow()
        assertEquals(1L, field(alice, group.groupId, "memberCount"))
    }

    // ── The invite flag ──────────────────────────────────────────────────────

    /**
     * The app refuses to join a group whose invite is off, and there is no
     * screen to turn it back on, so a joiner who switched it off closed the
     * group to everyone after them.
     */
    @Test
    fun aJoinerCannotSwitchTheInviteOff() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 10)
        val mallory = newUser()

        val closing = runCatching {
            mallory.firestore.runBatch { batch ->
                batch.set(membershipRef(mallory, mallory.uid, group.groupId), joinerMembership(mallory.uid, group.groupId))
                batch.update(
                    groupRef(mallory, group.groupId),
                    mapOf("memberCount" to FieldValue.increment(1), "inviteLinkActive" to false),
                )
            }.await()
        }
        assertTrue("Closing the invite on a join with room must be refused, got $closing", closing.isFailure)
        assertEquals(true, field(alice, group.groupId, "inviteLinkActive"))
    }

    /** The pair: the join that fills the group still closes it. */
    @Test
    fun theJoinThatFillsTheGroupStillClosesTheInvite() = runBlocking {
        val (group, alice, _) = pair(cap = 2)
        assertEquals(false, field(alice, group.groupId, "inviteLinkActive"))
    }

    @Test
    fun aLeaverCannotSwitchTheInviteOff() = runBlocking {
        val (group, alice, bob) = pair()

        val closing = runCatching {
            bob.firestore.runBatch { batch ->
                batch.update(groupRef(bob, group.groupId), mapOf("memberCount" to 1, "inviteLinkActive" to false))
                batch.delete(membershipRef(bob, bob.uid, group.groupId))
            }.await()
        }
        assertTrue("Closing the invite on the way out must be refused, got $closing", closing.isFailure)
        assertEquals(true, field(alice, group.groupId, "inviteLinkActive"))
    }

    /** The pair: leaving a full group reopens its invite. */
    @Test
    fun leavingAFullGroupStillReopensTheInvite() = runBlocking {
        val (group, alice, bob) = pair(cap = 2)
        assertEquals(false, field(alice, group.groupId, "inviteLinkActive"))

        groupsOf(bob).removeMember(group.groupId, bob.uid, keepOut = false).getOrThrow()

        assertEquals(true, field(alice, group.groupId, "inviteLinkActive"))
    }

    // ── The step cache and its week marker ──────────────────────────────────

    /** M4: the group total Home shows is bounded like each member's row. */
    @Test
    fun theGroupStepCacheIsBounded() = runBlocking {
        val (group, _, bob) = pair()
        val thisWeek = LocalDate.now().minusDays(1).toString()

        val absurd = groupsOf(bob).publishWeeklyTotal(group.groupId, 42_000_001L, thisWeek)
        assertTrue("A total past 20 x 2.1M must be refused, got $absurd", absurd.isFailure)

        groupsOf(bob).publishWeeklyTotal(group.groupId, 42_000_000L, thisWeek).getOrThrow()
        groupsOf(bob).publishWeeklyTotal(group.groupId, 81_234L, thisWeek).getOrThrow()
    }

    /**
     * The weekly finalizer closes the week the marker moves away from. A marker
     * a week ahead closed this week early and, written again, every week after
     * it as missed: the streak gone and shields spent. One far in the past made
     * a new history week per write.
     */
    @Test
    fun aMemberCannotMoveTheWeekMarkerOutOfTheCurrentWeek() = runBlocking {
        val (group, alice, bob) = pair()

        for (marker in listOf(
            LocalDate.now().plusDays(7).toString(),
            LocalDate.now().plusDays(3).toString(),
            LocalDate.now().minusDays(21).toString(),
            "1900-01-01",
        )) {
            val moved = groupsOf(bob).publishWeeklyTotal(group.groupId, 1_000L, marker)
            assertTrue("A week marker of $marker must be refused, got $moved", moved.isFailure)
        }
        assertEquals("", field(alice, group.groupId, "weekStart"))
    }

    /** The pair: this week's start, and last week's, which a late sync still publishes. */
    @Test
    fun thisWeeksAndLastWeeksMarkersStillPublish() = runBlocking {
        val (group, alice, bob) = pair()

        groupsOf(bob).publishWeeklyTotal(group.groupId, 50_000L, LocalDate.now().minusDays(7).toString()).getOrThrow()
        groupsOf(bob).publishWeeklyTotal(group.groupId, 10_000L, LocalDate.now().toString()).getOrThrow()

        assertEquals(LocalDate.now().toString(), field(alice, group.groupId, "weekStart"))
    }

    // ── Settings and creation ────────────────────────────────────────────────

    /** The server's reminders and finalizer read the start day. */
    @Test
    fun theStartDayMustBeADay() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val settings = groupsOf(alice)

        val garbage = settings.updateGroupSettings(group.groupId, group.name, 70_000, 10, false, "x".repeat(5_000))
        assertTrue("A start day that is not a day must be refused, got $garbage", garbage.isFailure)

        settings.updateGroupSettings(group.groupId, group.name, 70_000, 10, false, "SUNDAY").getOrThrow()
        assertEquals("SUNDAY", field(alice, group.groupId, "weekStartDay"))
    }

    /**
     * Everyone in the group downloads its document on every change, so create
     * takes GroupDto's keys and no others, and the stored id is the real one.
     */
    @Test
    fun aGroupCannotBeCreatedWithExtraFieldsOrAnotherId() = runBlocking<Unit> {
        val alice = newUser()

        // GroupDto's fields as the repository writes them, plus what the test adds.
        suspend fun rawCreate(group: Group, extra: Map<String, Any> = emptyMap()) = runCatching {
            val data = mapOf(
                "groupId" to group.groupId, "name" to group.name, "adminId" to group.adminId,
                "weeklyTarget" to group.weeklyTarget, "dailyPerPersonTarget" to 0,
                "maxMemberCap" to group.maxMemberCap, "memberCount" to 1,
                "canMembersEditTarget" to false, "inviteLink" to "",
                "inviteLinkActive" to true, "createdAt" to group.createdAt,
                "weeklySteps" to 0L, "weekStart" to "", "weekStartDay" to group.weekStartDay,
                "photoUrl" to "", "planningTimeZone" to "", "plannedWeekStart" to "",
                "streakWeeks" to 0, "streakThroughWeek" to "", "streakShields" to 0,
                "lastShieldedWeek" to "",
            ) + extra
            groupRef(alice, group.groupId).set(data).await()
        }

        val padded = rawCreate(newGroup(alice), mapOf("padding" to "x".repeat(10_000)))
        assertTrue("An unknown key must be refused, got $padded", padded.isFailure)

        val impostor = rawCreate(newGroup(alice), mapOf("groupId" to UUID.randomUUID().toString()))
        assertTrue("A stored id unlike the document's must be refused, got $impostor", impostor.isFailure)

        val badDay = rawCreate(newGroup(alice).copy(weekStartDay = "Funday"))
        assertTrue("A start day that is not a day must be refused, got $badDay", badDay.isFailure)

        // The pair: the same write, as the repository makes it.
        assertTrue(rawCreate(newGroup(alice)).isSuccess)
        groupsOf(alice).createGroup(newGroup(alice)).getOrThrow()
    }

    /**
     * A group id is the invite key in every link and QR code it shared, so a
     * deleted group's id cannot be claimed by a new group. The server writes
     * the tombstone (functions-notifications cleanUpDeletedGroup).
     */
    @Test
    fun aDeletedGroupsIdCannotBeTakenOver() = runBlocking<Unit> {
        val mallory = newUser()
        val deletedId = UUID.randomUUID().toString()
        FirebaseEmulator.seedServerDocument("deletedGroups", deletedId, mapOf("deletedAt" to java.time.Instant.now()))

        val takeover = groupsOf(mallory).createGroup(newGroup(mallory).copy(groupId = deletedId))
        assertTrue("Creating a group under a deleted group's id must be refused, got $takeover", takeover.isFailure)

        // The pair: a fresh id, as the app always uses.
        groupsOf(mallory).createGroup(newGroup(mallory)).getOrThrow()
    }

    private fun joinerMembership(userId: String, groupId: String) = mapOf(
        "userId" to userId,
        "groupId" to groupId,
        "displayName" to "Mallory",
        "role" to "member",
        "canEditTarget" to false,
        "photoUrl" to "",
        "joinedAt" to System.currentTimeMillis(),
        "weeklySteps" to 0L,
        "weekStart" to "",
        "todaySteps" to 0L,
        "todayDate" to "",
        "walkedDays" to 0,
        "walkedWeekStart" to "",
    )
}
