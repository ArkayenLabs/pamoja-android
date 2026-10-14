package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FieldValue
import com.pamoja.app.domain.model.Group
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Who can become, stay and stop being a member, and what a membership may say.
 *
 * The August 2026 review's membership findings (C1 forging, H2 repointing, H3
 * backdated joinedAt, M2 off-domain photos) were fixed in `firestore.rules`
 * but never pinned by a test; they are here now. So are the October 2026
 * review's: a member raising the count without anyone joining, a join and
 * self-delete loop filling a group with nobody, any invite holder reading the
 * starter's membership, and a joiner claiming the admin role or extra fields.
 *
 * In pairs, as everywhere in this suite: each refusal sits beside the real
 * write it must not break. The attacks are raw Firestore writes, shaped like
 * the repository's own commits, because the repository never makes them.
 *
 * Requires the emulators, see [FirebaseEmulator].
 */
@RunWith(AndroidJUnit4::class)
class MembershipIntegrityRulesEmulatorTest {

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

    private fun usersOf(client: FirebaseEmulator.Client) =
        FirebaseUserRepositoryImpl(client.firestore, FirebaseAdventureExporter(client.functions, client.auth))

    private suspend fun createGroup(admin: FirebaseEmulator.Client, cap: Int = 10): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "Integrity group",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = cap,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        groupsOf(admin).createGroup(group).getOrThrow()
        return group
    }

    private fun membershipRef(client: FirebaseEmulator.Client, userId: String, groupId: String) =
        client.firestore.collection("memberships").document("${userId}_$groupId")

    private fun groupRef(client: FirebaseEmulator.Client, groupId: String) =
        client.firestore.collection("groups").document(groupId)

    private suspend fun memberCount(client: FirebaseEmulator.Client, groupId: String): Long =
        groupRef(client, groupId).get().await().getLong("memberCount") ?: error("no memberCount")

    /** What joinGroup() writes for a membership, field for field. */
    private fun membershipData(userId: String, groupId: String) = mutableMapOf<String, Any>(
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

    /** A join commit as joinGroup() makes it, with whatever the caller changes. */
    private suspend fun rawJoin(
        client: FirebaseEmulator.Client,
        groupId: String,
        membership: Map<String, Any> = membershipData(client.uid, groupId),
        groupUpdate: Map<String, Any> = mapOf("memberCount" to FieldValue.increment(1)),
    ) = runCatching {
        client.firestore.runBatch { batch ->
            batch.set(membershipRef(client, client.uid, groupId), membership)
            batch.update(groupRef(client, groupId), groupUpdate)
        }.await()
    }

    // ── Becoming a member ────────────────────────────────────────────────────

    /** C1: a membership with no join to go with it, then the roster it opened. */
    @Test
    fun aStrangerCannotWriteThemselvesIntoAGroup() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 2)
        val mallory = newUser()

        val forged = runCatching {
            membershipRef(mallory, mallory.uid, group.groupId).set(membershipData(mallory.uid, group.groupId)).await()
        }
        assertTrue("A membership without a join must be refused, got $forged", forged.isFailure)

        val roster = runCatching {
            mallory.firestore.collection("memberships").whereEqualTo("groupId", group.groupId).get().await()
        }
        assertTrue("A non-member must not list the roster, got $roster", roster.isFailure)
        assertEquals(1L, memberCount(alice, group.groupId))
    }

    /** The pair: a real join, and the cap still binding it. */
    @Test
    fun aRealJoinStillWorksAndTheCapStillHolds() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 2)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        assertEquals(2L, memberCount(alice, group.groupId))

        val carol = newUser()
        assertTrue("A third member in a cap-2 group", rawJoin(carol, group.groupId).isFailure)
        assertEquals(2L, memberCount(alice, group.groupId))
    }

    /**
     * Rules case A's getAfter() finds an existing member's old membership just
     * as it finds a new one, so before 2026-10-13 a member could add one to the
     * count per write until the group read as full.
     */
    @Test
    fun aMemberCannotRaiseTheCountWithoutAnyoneJoining() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 5)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()

        val bump = runCatching {
            groupRef(bob, group.groupId).update("memberCount", FieldValue.increment(1)).await()
        }
        assertTrue("An increment with no new membership must be refused, got $bump", bump.isFailure)
        assertEquals(2L, memberCount(alice, group.groupId))

        // The pair: someone genuinely joining still moves it.
        val carol = newUser()
        groupsOf(carol).joinGroup(group.groupId, carol.uid).getOrThrow()
        assertEquals(3L, memberCount(alice, group.groupId))
    }

    /** H3: the successor admin is the earliest joinedAt, so it may not predate the group. */
    @Test
    fun aJoinerCannotBackdateJoinedAt() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val mallory = newUser()

        val backdated = rawJoin(
            mallory,
            group.groupId,
            membership = membershipData(mallory.uid, group.groupId) + ("joinedAt" to 0L),
        )
        assertTrue("joinedAt 0 must be refused, got $backdated", backdated.isFailure)

        assertTrue("The same join, honestly dated", rawJoin(mallory, group.groupId).isSuccess)
    }

    /**
     * Every member's leaderboard downloads every membership, so its key set is
     * closed; and only the group's admin may carry the admin role.
     */
    @Test
    fun aJoinerCannotClaimTheAdminRoleOrCarryExtraFields() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val mallory = newUser()

        val asAdmin = rawJoin(
            mallory,
            group.groupId,
            membership = membershipData(mallory.uid, group.groupId) + mapOf("role" to "admin", "canEditTarget" to true),
        )
        assertTrue("A joiner as admin must be refused, got $asAdmin", asAdmin.isFailure)

        val padded = rawJoin(
            mallory,
            group.groupId,
            membership = membershipData(mallory.uid, group.groupId) + ("padding" to "x".repeat(10_000)),
        )
        assertTrue("An unknown key must be refused, got $padded", padded.isFailure)

        // The pair: the app's own join, and the admin membership createGroup()
        // wrote above with role admin and canEditTarget true.
        groupsOf(mallory).joinGroup(group.groupId, mallory.uid).getOrThrow()
        assertEquals("admin", membershipRef(alice, alice.uid, group.groupId).get().await().getString("role"))
    }

    // ── Staying a member ─────────────────────────────────────────────────────

    /** H2: pointing your membership at another group puts you on its board. */
    @Test
    fun aMemberCannotRepointTheirMembershipAtAnotherGroup() = runBlocking {
        val alice = newUser()
        val groupA = createGroup(alice)
        val groupB = createGroup(alice)
        val bob = newUser()
        groupsOf(bob).joinGroup(groupA.groupId, bob.uid).getOrThrow()

        val repointed = runCatching {
            membershipRef(bob, bob.uid, groupA.groupId).update("groupId", groupB.groupId).await()
        }
        assertTrue("groupId must be immutable, got $repointed", repointed.isFailure)

        val backdated = runCatching {
            membershipRef(bob, bob.uid, groupA.groupId).update("joinedAt", 0L).await()
        }
        assertTrue("joinedAt must be immutable, got $backdated", backdated.isFailure)

        // The pair: the sync's own publish.
        groupsOf(bob).publishMyWeeklySteps(
            groupA.groupId, bob.uid, 12_000L, "2026-10-12", 3_000L, "2026-10-13", 0b11,
        ).getOrThrow()
    }

    /** M2: a photo URL is fetched by every other member's phone, so it must be our bucket. */
    @Test
    fun aPhotoMustComeFromThisProjectsStorage() = runBlocking<Unit> {
        val alice = newUser()
        val group = createGroup(alice)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        val bobs = membershipRef(bob, bob.uid, group.groupId)

        val elsewhere = runCatching { bobs.update("photoUrl", "https://tracker.example/pixel.jpg").await() }
        assertTrue("An off-domain photo must be refused, got $elsewhere", elsewhere.isFailure)

        bobs.update(
            "photoUrl",
            "https://firebasestorage.googleapis.com/v0/b/pamoja.appspot.com/o/avatars%2Fx%2Fprofile.jpg?alt=media",
        ).await()
        bobs.update("photoUrl", "").await()
    }

    /**
     * The group document names its adminId, and anyone signed in with the
     * invite may read the group document. Before 2026-10-13 that was enough to
     * read the starter's membership: name, photo and live step figures.
     */
    @Test
    fun anInviteHolderCannotReadTheStartersMembership() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val carol = newUser()

        val adminId = groupRef(carol, group.groupId).get().await().getString("adminId")
        assertEquals("The invite still shows the group", alice.uid, adminId)

        val peek = runCatching { membershipRef(carol, alice.uid, group.groupId).get().await() }
        assertTrue("A non-member must not read another person's membership, got $peek", peek.isFailure)
    }

    /**
     * The pair. joinGroup() reads the joiner's own membership before it exists,
     * which has to be empty rather than denied, and members still read each
     * other's.
     */
    @Test
    fun yourOwnMembershipAndYourGroupmatesStillRead() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val bob = newUser()

        val notYet = membershipRef(bob, bob.uid, group.groupId).get().await()
        assertFalse("Not a member yet, so nothing there", notYet.exists())

        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        assertTrue(membershipRef(bob, alice.uid, group.groupId).get().await().exists())
        assertTrue(membershipRef(alice, bob.uid, group.groupId).get().await().exists())
    }

    // ── Stopping being a member ──────────────────────────────────────────────

    /**
     * Join, delete your own membership, join again: before 2026-10-13 each lap
     * left the count one higher with nobody added, until the group read as
     * full. Anyone holding the invite could do it.
     */
    @Test
    fun leavingWithoutBringingTheCountDownIsRefused() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 5)
        val mallory = newUser()
        groupsOf(mallory).joinGroup(group.groupId, mallory.uid).getOrThrow()

        val vanish = runCatching { membershipRef(mallory, mallory.uid, group.groupId).delete().await() }
        assertTrue("A self-delete with no decrement must be refused, got $vanish", vanish.isFailure)
        assertEquals(2L, memberCount(alice, group.groupId))
        assertTrue(membershipRef(alice, mallory.uid, group.groupId).get().await().exists())
    }

    /** The pair: leaving through the app, which decrements in the same transaction. */
    @Test
    fun aRealLeaveStillWorks() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 5)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()

        groupsOf(bob).removeMember(group.groupId, bob.uid, keepOut = false).getOrThrow()

        assertEquals(1L, memberCount(alice, group.groupId))
        assertFalse(membershipRef(alice, bob.uid, group.groupId).get().await().exists())
    }

    /** The admin's removal is unchanged. */
    @Test
    fun theAdminCanStillRemoveAMember() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 5)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()

        groupsOf(alice).removeMember(group.groupId, bob.uid, keepOut = true).getOrThrow()

        assertEquals(1L, memberCount(alice, group.groupId))
    }

    /** Account deletion, every shape it takes, under the tighter delete rule. */
    @Test
    fun accountDeletionStillWorksForAMember() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 5)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()

        usersOf(bob).deleteAllUserData(bob.uid).getOrThrow()

        assertEquals(1L, memberCount(alice, group.groupId))
        assertFalse(membershipRef(alice, bob.uid, group.groupId).get().await().exists())
    }

    @Test
    fun accountDeletionStillWorksForAnAdminHandingOver() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 5)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()

        usersOf(alice).deleteAllUserData(alice.uid).getOrThrow()

        val after = groupRef(bob, group.groupId).get().await()
        assertEquals(bob.uid, after.getString("adminId"))
        assertEquals(1L, after.getLong("memberCount"))
    }

    @Test
    fun accountDeletionStillWorksForASoleAdmin() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 5)

        usersOf(alice).deleteAllUserData(alice.uid).getOrThrow()

        assertFalse(membershipRef(alice, alice.uid, group.groupId).get().await().exists())
        val gone = runCatching { groupRef(alice, group.groupId).get().await().exists() }
        assertEquals(false, gone.getOrNull())
    }

    /** A membership whose group was deleted underneath it can still be erased. */
    @Test
    fun accountDeletionStillWorksForAnOrphanedMembership() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, cap = 5)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        groupRef(alice, group.groupId).delete().await()

        usersOf(bob).deleteAllUserData(bob.uid).getOrThrow()

        assertFalse(membershipRef(bob, bob.uid, group.groupId).get().await().exists())
    }
}
