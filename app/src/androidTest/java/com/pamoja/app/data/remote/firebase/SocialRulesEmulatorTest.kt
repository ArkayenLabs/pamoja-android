package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FieldValue
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.SignalKind
import com.pamoja.app.domain.model.WalkedDays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/**
 * The social loop's three additions to the rules: cheers and nudges
 * (`groups/{groupId}/signals`), the day dots on memberships (`walkedDays`)
 * and the server-owned group streak.
 *
 * In pairs, as everywhere in this suite: each refusal sits beside the real
 * write or query it must not break, because a rule that refuses everything
 * passes every "attacker is blocked" test on its own.
 *
 * Requires the emulators, see [FirebaseEmulator].
 */
@RunWith(AndroidJUnit4::class)
class SocialRulesEmulatorTest {

    private val clients = mutableListOf<FirebaseEmulator.Client>()
    private val today = LocalDate.now().toString()

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

    private fun signalsOf(client: FirebaseEmulator.Client) = FirebaseSignalRepositoryImpl(client.firestore)

    private suspend fun createGroup(admin: FirebaseEmulator.Client): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "Social group",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        groupsOf(admin).createGroup(group).getOrThrow()
        return group
    }

    /** Alice's group with Bob in it. */
    private suspend fun pair(): Triple<Group, FirebaseEmulator.Client, FirebaseEmulator.Client> {
        val alice = newUser()
        val bob = newUser()
        val group = createGroup(alice)
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        return Triple(group, alice, bob)
    }

    /** A raw signal write, for the shapes the repository would never produce. */
    private suspend fun rawSignal(
        client: FirebaseEmulator.Client,
        groupId: String,
        id: String,
        data: Map<String, Any>,
    ) = runCatching {
        client.firestore.collection("groups").document(groupId)
            .collection("signals").document(id).set(data).await()
    }

    private fun signalData(kind: String, from: String, to: String, date: String) = mapOf(
        "kind" to kind,
        "fromUserId" to from,
        "toUserId" to to,
        "date" to date,
        "createdAt" to FieldValue.serverTimestamp(),
    )

    // ── Cheers and nudges ────────────────────────────────────────────────────

    /** The real flow: a cheer lands and both people involved can see it. */
    @Test
    fun aMemberCanCheerAnotherAndBothOfThemSeeIt() = runBlocking {
        val (group, alice, bob) = pair()

        signalsOf(alice).send(group.groupId, SignalKind.CHEER, alice.uid, listOf(bob.uid), today).getOrThrow()

        val sent = withTimeout(10_000) {
            signalsOf(alice).observeSent(group.groupId, alice.uid, today).first { it.isNotEmpty() }
        }
        val received = withTimeout(10_000) {
            signalsOf(bob).observeReceived(group.groupId, bob.uid, today).first { it.isNotEmpty() }
        }
        assertEquals(SignalKind.CHEER, sent.single().kind)
        assertEquals(bob.uid, sent.single().toUserId)
        assertEquals(alice.uid, received.single().fromUserId)
    }

    /**
     * Private to the two people involved. A list of who was nudged is a list
     * of who is falling behind, and nobody else in the group gets to read it.
     */
    @Test
    fun aThirdMemberCannotReadOtherPeoplesSignals() = runBlocking {
        val (group, alice, bob) = pair()
        val carol = newUser()
        groupsOf(carol).joinGroup(group.groupId, carol.uid).getOrThrow()
        signalsOf(alice).send(group.groupId, SignalKind.NUDGE, alice.uid, listOf(bob.uid), today).getOrThrow()

        val signals = carol.firestore.collection("groups").document(group.groupId).collection("signals")
        val unfiltered = runCatching { signals.get().await().size() }
        val asBob = runCatching { signals.whereEqualTo("toUserId", bob.uid).get().await().size() }
        val asAlice = runCatching { signals.whereEqualTo("fromUserId", alice.uid).get().await().size() }

        assertTrue("An unfiltered read must be refused, got $unfiltered", unfiltered.isFailure)
        assertTrue("Carol must not read Bob's nudges, got $asBob", asBob.isFailure)
        assertTrue("Carol must not read Alice's nudges, got $asAlice", asAlice.isFailure)
        // Carol's own, empty, view still works: the refusal is about whose, not about her.
        assertEquals(0, signalsOf(carol).observeReceived(group.groupId, carol.uid, today).first().size)
    }

    /** One cheer per person per day, by the ID; a nudge the same day is separate. */
    @Test
    fun aSecondCheerTheSameDayIsRefusedButANudgeIsNot() = runBlocking {
        val (group, alice, bob) = pair()
        signalsOf(alice).send(group.groupId, SignalKind.CHEER, alice.uid, listOf(bob.uid), today).getOrThrow()

        val again = signalsOf(alice).send(group.groupId, SignalKind.CHEER, alice.uid, listOf(bob.uid), today)
        val nudge = signalsOf(alice).send(group.groupId, SignalKind.NUDGE, alice.uid, listOf(bob.uid), today)

        assertTrue("A second cheer to the same person on the same day must be refused", again.isFailure)
        assertTrue("A nudge is a different signal and must still go, got $nudge", nudge.isSuccess)
    }

    /** A future date would mint another day's allowance. Today, from the pair test above, is fine. */
    @Test
    fun aDateFarFromTodayIsRefused() = runBlocking {
        val (group, alice, bob) = pair()
        val future = LocalDate.now().plusDays(3).toString()
        val past = LocalDate.now().minusDays(3).toString()

        val ahead = signalsOf(alice).send(group.groupId, SignalKind.NUDGE, alice.uid, listOf(bob.uid), future)
        val behind = signalsOf(alice).send(group.groupId, SignalKind.NUDGE, alice.uid, listOf(bob.uid), past)
        val now = signalsOf(alice).send(group.groupId, SignalKind.NUDGE, alice.uid, listOf(bob.uid), today)

        assertTrue("A date three days ahead must be refused, got $ahead", ahead.isFailure)
        assertTrue("A date three days back must be refused, got $behind", behind.isFailure)
        assertTrue("Today must go, got $now", now.isSuccess)
    }

    /** Holding a group ID is not membership: invite links hand it to anyone. */
    @Test
    fun aStrangerCannotSendIntoAGroup() = runBlocking {
        val (group, _, bob) = pair()
        val stranger = newUser()

        val sent = signalsOf(stranger).send(group.groupId, SignalKind.NUDGE, stranger.uid, listOf(bob.uid), today)

        assertTrue("A non-member must not reach a member, got $sent", sent.isFailure)
    }

    /** Nor may a member reach somebody outside the group through it. */
    @Test
    fun aMemberCannotSendToSomeoneOutsideTheGroup() = runBlocking {
        val (group, alice, _) = pair()
        val outsider = newUser()

        val sent = signalsOf(alice).send(group.groupId, SignalKind.CHEER, alice.uid, listOf(outsider.uid), today)

        assertTrue("The recipient must be a member, got $sent", sent.isFailure)
    }

    /** The sender is always the signed-in account, and the ID must say so. */
    @Test
    fun aSignalCannotBeForgedFromSomeoneElse() = runBlocking {
        val (group, alice, bob) = pair()
        val carol = newUser()
        groupsOf(carol).joinGroup(group.groupId, carol.uid).getOrThrow()

        val asAlice = rawSignal(
            carol, group.groupId, FirebaseSignalRepositoryImpl.idOf(today, SignalKind.CHEER, alice.uid, bob.uid),
            signalData("cheer", alice.uid, bob.uid, today),
        )
        val mismatchedId = rawSignal(
            carol, group.groupId, FirebaseSignalRepositoryImpl.idOf(today, SignalKind.CHEER, carol.uid, alice.uid),
            signalData("cheer", carol.uid, bob.uid, today),
        )

        assertTrue("Carol must not send as Alice, got $asAlice", asAlice.isFailure)
        assertTrue("The ID must name the real recipient, got $mismatchedId", mismatchedId.isFailure)
    }

    /** Only the five known keys, only the two known kinds, never to yourself. */
    @Test
    fun malformedSignalsAreRefused() = runBlocking {
        val (group, alice, bob) = pair()
        val withMessage = rawSignal(
            alice, group.groupId, FirebaseSignalRepositoryImpl.idOf(today, SignalKind.CHEER, alice.uid, bob.uid),
            signalData("cheer", alice.uid, bob.uid, today) + ("message" to "anything at all"),
        )
        val unknownKind = rawSignal(
            alice, group.groupId, "${today}_poke_${alice.uid}_${bob.uid}",
            signalData("poke", alice.uid, bob.uid, today),
        )
        val toSelf = signalsOf(alice).send(group.groupId, SignalKind.CHEER, alice.uid, listOf(alice.uid), today)

        assertTrue("A free-text field must be refused, got $withMessage", withMessage.isFailure)
        assertTrue("An unknown kind must be refused, got $unknownKind", unknownKind.isFailure)
        assertTrue("Cheering yourself must be refused, got $toSelf", toSelf.isFailure)
    }

    /** Create-only: a signal cannot be deleted to send it again. */
    @Test
    fun aSignalCannotBeDeletedOrRewritten() = runBlocking {
        val (group, alice, bob) = pair()
        signalsOf(alice).send(group.groupId, SignalKind.NUDGE, alice.uid, listOf(bob.uid), today).getOrThrow()
        val doc = alice.firestore.collection("groups").document(group.groupId).collection("signals")
            .document(FirebaseSignalRepositoryImpl.idOf(today, SignalKind.NUDGE, alice.uid, bob.uid))

        val deleted = runCatching { doc.delete().await() }
        val rewritten = runCatching { doc.update("date", today).await() }

        assertTrue("Delete must be refused, got $deleted", deleted.isFailure)
        assertTrue("Update must be refused, got $rewritten", rewritten.isFailure)
    }

    // ── Day dots on memberships ──────────────────────────────────────────────

    /** The real write: the worker's publish now carries the mask and its marker. */
    @Test
    fun aMemberCanPublishTheirWalkedDays() = runBlocking {
        val (group, alice, _) = pair()
        val monday = "2026-09-28"

        groupsOf(alice).publishMyWeeklySteps(group.groupId, alice.uid, 9_000, monday, 3_000, monday, 0b0000101)
            .getOrThrow()

        val mine = groupsOf(alice).getGroupMemberships(group.groupId).first().first { it.userId == alice.uid }
        assertEquals(0b0000101, mine.walkedDays)
        assertEquals(monday, mine.walkedWeekStart)
    }

    /** Seven bits and no more; a negative or wider mask is not a week. */
    @Test
    fun anImpossibleMaskIsRefused() = runBlocking {
        val (group, alice, _) = pair()
        val mine = alice.firestore.collection("memberships").document("${alice.uid}_${group.groupId}")

        val tooWide = runCatching { mine.update("walkedDays", WalkedDays.ALL + 1).await() }
        val negative = runCatching { mine.update("walkedDays", -1).await() }
        val badMarker = runCatching { mine.update("walkedWeekStart", "last week").await() }

        assertTrue("128 must be refused, got $tooWide", tooWide.isFailure)
        assertTrue("-1 must be refused, got $negative", negative.isFailure)
        assertTrue("A marker that is not a date must be refused, got $badMarker", badMarker.isFailure)
    }

    /**
     * The backward-compatibility promise. Older app versions keep publishing
     * the four figures they know, and renaming yourself still fans out, on a
     * membership a newer version has already stamped with walked days.
     */
    @Test
    fun olderAppVersionsWritesStillPass() = runBlocking {
        val (group, alice, _) = pair()
        val mine = alice.firestore.collection("memberships").document("${alice.uid}_${group.groupId}")
        groupsOf(alice).publishMyWeeklySteps(group.groupId, alice.uid, 9_000, "2026-09-28", 3_000, "2026-09-28", 0b11)
            .getOrThrow()

        // Exactly what 1.0.12's publishMyWeeklySteps and profile fan-out send.
        val oldPublish = runCatching {
            mine.update(
                mapOf(
                    "weeklySteps" to 12_000L,
                    "weekStart" to "2026-09-28",
                    "todaySteps" to 4_000L,
                    "todayDate" to "2026-09-29",
                ),
            ).await()
        }
        val oldFanOut = runCatching { mine.update(mapOf("displayName" to "Alice B", "photoUrl" to "")).await() }

        assertTrue("An older version's publish must still pass, got $oldPublish", oldPublish.isSuccess)
        assertTrue("An older version's rename must still pass, got $oldFanOut", oldFanOut.isSuccess)
    }

    // ── The group streak is server-owned ─────────────────────────────────────

    /** Nobody may start a group with a streak it never earned. Normal creation is every other test here. */
    @Test
    fun aGroupCannotBeCreatedWithAStreak() = runBlocking {
        val alice = newUser()
        val groupId = UUID.randomUUID().toString()
        val forged = runCatching {
            alice.firestore.collection("groups").document(groupId).set(
                mapOf(
                    "groupId" to groupId,
                    "name" to "Instant legends",
                    "adminId" to alice.uid,
                    "weeklyTarget" to 70_000,
                    "maxMemberCap" to 10,
                    "memberCount" to 1,
                    "createdAt" to System.currentTimeMillis(),
                    "streakWeeks" to 52,
                    "streakThroughWeek" to "2026-09-21",
                ),
            ).await()
        }

        assertTrue("A streak baked in at creation must be refused, got $forged", forged.isFailure)
    }

    /**
     * Shields are the finalizer's alone. The app's own create writes the two
     * displayed fields at their defaults (every other test here creates a
     * group that way); anything more is refused.
     */
    @Test
    fun aGroupCannotBeCreatedHoldingShields() = runBlocking {
        val alice = newUser()
        suspend fun forged(extra: Map<String, Any>) = runCatching {
            val groupId = UUID.randomUUID().toString()
            alice.firestore.collection("groups").document(groupId).set(
                mapOf(
                    "groupId" to groupId, "name" to "Shielded", "adminId" to alice.uid,
                    "weeklyTarget" to 70_000, "maxMemberCap" to 10, "memberCount" to 1,
                    "createdAt" to System.currentTimeMillis(),
                ) + extra,
            ).await()
        }

        assertTrue("streakShields 2 must be refused", forged(mapOf("streakShields" to 2)).isFailure)
        assertTrue("shieldsStarted must be refused", forged(mapOf("shieldsStarted" to true)).isFailure)
        assertTrue("shieldProgress must be refused", forged(mapOf("shieldProgress" to 3)).isFailure)
        assertTrue(
            "the app's own defaults must pass",
            forged(mapOf("streakShields" to 0, "lastShieldedWeek" to "")).isSuccess,
        )
    }

    @Test
    fun shieldsCannotBeWrittenByAnyClient() = runBlocking {
        val (group, alice, _) = pair()
        val shields = runCatching {
            alice.firestore.collection("groups").document(group.groupId)
                .update(mapOf("streakShields" to 2, "shieldsStarted" to true)).await()
        }

        assertTrue("The admin must not grant shields, got $shields", shields.isFailure)
    }

    /** Nor raise one later, admin or not. The admin's own settings edit is the pair. */
    @Test
    fun theStreakCannotBeWrittenByAnyClient() = runBlocking {
        val (group, alice, bob) = pair()
        val groupDoc = { c: FirebaseEmulator.Client -> c.firestore.collection("groups").document(group.groupId) }

        val byAdmin = runCatching { groupDoc(alice).update("streakWeeks", 10).await() }
        val byMember = runCatching {
            groupDoc(bob).update(mapOf("streakWeeks" to 10, "streakThroughWeek" to "2026-09-21")).await()
        }
        val adminRename = runCatching { groupDoc(alice).update("name", "Renamed").await() }

        assertTrue("The admin must not set the streak, got $byAdmin", byAdmin.isFailure)
        assertTrue("A member must not set the streak, got $byMember", byMember.isFailure)
        assertTrue("The admin's normal edit must still work, got $adminRename", adminRename.isSuccess)
    }
}
