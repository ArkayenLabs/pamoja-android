package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.pamoja.app.domain.model.Group
import kotlinx.coroutines.flow.first
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
 * Removing someone keeps them out (2026-10-14, SECURITY_REVIEW.md O1).
 *
 * The invite key is the group ID and never changes, so before this a removed
 * person tapped the same link and was straight back in. Now the admin's removal
 * lists them in `groups/{id}/removed/{uid}` in the same commit, the memberships
 * create rule refuses anyone listed, and only the admin takes them off it.
 *
 * In pairs, as everywhere in this suite: each refusal sits beside the real use
 * it must not break. Requires the emulators, see [FirebaseEmulator].
 */
@RunWith(AndroidJUnit4::class)
class RemovedMembersRulesEmulatorTest {

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

    private suspend fun createGroup(admin: FirebaseEmulator.Client): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "Removal group",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        groupsOf(admin).createGroup(group).getOrThrow()
        return group
    }

    private fun removedRef(client: FirebaseEmulator.Client, groupId: String, userId: String) =
        client.firestore.collection("groups").document(groupId).collection("removed").document(userId)

    private suspend fun memberCount(client: FirebaseEmulator.Client, groupId: String): Long =
        client.firestore.collection("groups").document(groupId).get().await().getLong("memberCount")
            ?: error("no memberCount")

    private suspend fun isMember(client: FirebaseEmulator.Client, userId: String, groupId: String): Boolean =
        client.firestore.collection("memberships").document("${userId}_$groupId").get().await().exists()

    /** What the repository writes, so the attacks differ from it in one way each. */
    private fun entry(userId: String, removedAt: Any = FieldValue.serverTimestamp()) = mutableMapOf(
        "userId" to userId,
        "displayName" to "Bob",
        "removedAt" to removedAt,
    )

    // ── The removal holds ────────────────────────────────────────────────────

    @Test
    fun aRemovedMemberCannotRejoinWithTheLink() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()

        groupsOf(alice).removeMember(group.groupId, bob.uid, keepOut = true).getOrThrow()
        assertEquals(1L, memberCount(alice, group.groupId))

        val rejoin = groupsOf(bob).joinGroup(group.groupId, bob.uid)
        assertTrue("A removed member must not rejoin, got $rejoin", rejoin.isFailure)
        assertFalse(isMember(alice, bob.uid, group.groupId))
        assertEquals(1L, memberCount(alice, group.groupId))

        // What the invite preview reads, so it can say so before they tap Join.
        assertTrue(groupsOf(bob).isRemovedFrom(group.groupId, bob.uid).getOrThrow())
    }

    /** The pair: leaving on your own keeps nobody out. */
    @Test
    fun someoneWhoLeftOnTheirOwnCanRejoin() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()

        groupsOf(bob).removeMember(group.groupId, bob.uid, keepOut = false).getOrThrow()
        assertFalse(groupsOf(bob).isRemovedFrom(group.groupId, bob.uid).getOrThrow())

        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        assertTrue(isMember(alice, bob.uid, group.groupId))
        assertEquals(2L, memberCount(alice, group.groupId))
    }

    // ── Only the admin decides ───────────────────────────────────────────────

    @Test
    fun aRemovedMemberCannotLetThemselvesBackIn() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        groupsOf(alice).removeMember(group.groupId, bob.uid, keepOut = true).getOrThrow()

        val deleted = groupsOf(bob).letBackIn(group.groupId, bob.uid)
        assertTrue("Only the admin may delete the entry, got $deleted", deleted.isFailure)

        val rewritten = runCatching { removedRef(bob, group.groupId, bob.uid).set(entry(bob.uid)).await() }
        assertTrue("Only the admin may rewrite the entry, got $rewritten", rewritten.isFailure)

        assertTrue(groupsOf(bob).isRemovedFrom(group.groupId, bob.uid).getOrThrow())
        assertTrue(groupsOf(bob).joinGroup(group.groupId, bob.uid).isFailure)
    }

    /** The pair: the admin lets them back in, and the link works again. */
    @Test
    fun theAdminCanLetThemBackInAndTheyRejoin() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val bob = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        groupsOf(alice).removeMember(group.groupId, bob.uid, keepOut = true).getOrThrow()

        groupsOf(alice).letBackIn(group.groupId, bob.uid).getOrThrow()

        assertFalse(groupsOf(bob).isRemovedFrom(group.groupId, bob.uid).getOrThrow())
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        assertTrue(isMember(alice, bob.uid, group.groupId))

        // And removing them a second time still writes the entry.
        groupsOf(alice).removeMember(group.groupId, bob.uid, keepOut = true).getOrThrow()
        assertTrue(groupsOf(bob).joinGroup(group.groupId, bob.uid).isFailure)
    }

    @Test
    fun aMemberCannotKeepSomeoneElseOut() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val carol = newUser()
        groupsOf(carol).joinGroup(group.groupId, carol.uid).getOrThrow()
        val dave = newUser()

        val blocked = runCatching { removedRef(carol, group.groupId, dave.uid).set(entry(dave.uid)).await() }
        assertTrue("Only the admin may keep someone out, got $blocked", blocked.isFailure)

        // The pair: the person carol tried to keep out still joins.
        groupsOf(dave).joinGroup(group.groupId, dave.uid).getOrThrow()
        assertTrue(isMember(alice, dave.uid, group.groupId))
    }

    // ── Who can see the list ─────────────────────────────────────────────────

    @Test
    fun onlyTheAdminSeesTheRemovedList() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val bob = newUser()
        val carol = newUser()
        groupsOf(bob).joinGroup(group.groupId, bob.uid).getOrThrow()
        groupsOf(carol).joinGroup(group.groupId, carol.uid).getOrThrow()
        groupsOf(alice).removeMember(group.groupId, bob.uid, keepOut = true).getOrThrow()

        val asMember = runCatching { groupsOf(carol).observeRemovedMembers(group.groupId).first() }
        assertTrue("A member must not list who was removed, got $asMember", asMember.isFailure)
        val asRemoved = runCatching { groupsOf(bob).observeRemovedMembers(group.groupId).first() }
        assertTrue("The removed person must not list it either, got $asRemoved", asRemoved.isFailure)
        val othersEntry = runCatching { removedRef(carol, group.groupId, bob.uid).get().await() }
        assertTrue("A member must not read someone else's entry, got $othersEntry", othersEntry.isFailure)

        // The pair: the admin sees who they removed.
        val list = groupsOf(alice).observeRemovedMembers(group.groupId).first()
        assertEquals(listOf(bob.uid), list.map { it.userId })
    }

    // ── What an entry may hold ───────────────────────────────────────────────

    @Test
    fun anEntryHoldsOnlyTheNameAndTheServersTime() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice)
        val bob = newUser()

        val extra = runCatching {
            removedRef(alice, group.groupId, bob.uid).set(entry(bob.uid) + ("note" to "x".repeat(1000))).await()
        }
        assertTrue("Extra fields must be refused, got $extra", extra.isFailure)

        val backdated = runCatching {
            removedRef(alice, group.groupId, bob.uid).set(entry(bob.uid, removedAt = Timestamp(0, 0))).await()
        }
        assertTrue("removedAt must be the server's time, got $backdated", backdated.isFailure)

        val otherId = runCatching { removedRef(alice, group.groupId, bob.uid).set(entry(alice.uid)).await() }
        assertTrue("The entry must name the person in its ID, got $otherId", otherId.isFailure)

        val self = runCatching { removedRef(alice, group.groupId, alice.uid).set(entry(alice.uid)).await() }
        assertTrue("The admin cannot list themselves, got $self", self.isFailure)

        // The pair: the shape the repository writes is accepted.
        removedRef(alice, group.groupId, bob.uid).set(entry(bob.uid)).await()
        assertTrue(groupsOf(bob).joinGroup(group.groupId, bob.uid).isFailure)
    }
}
