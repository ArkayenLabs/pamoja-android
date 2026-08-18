package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.Group
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Who may read the memberships collection.
 *
 * This collection holds every user's display name, avatar URL and the groups
 * they belong to. Until 2026-08-18 the rule was a bare `allow read: if
 * isSignedIn()`, and `read` covers `list`, so any account that had just signed
 * up could run an unfiltered query and take the lot.
 *
 * The tests come in pairs on purpose. Closing a read hole is easy; closing it
 * without breaking the queries the app actually makes is the hard half, and a
 * test that only proved the stranger was blocked would pass just as happily if
 * members had been locked out too.
 *
 * Requires the emulators, see [FirebaseEmulator].
 */
@RunWith(AndroidJUnit4::class)
class MembershipReadRulesEmulatorTest {

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

    private suspend fun createGroup(admin: FirebaseEmulator.Client, name: String): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = name,
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        FirebaseGroupRepositoryImpl(admin.firestore).createGroup(group).getOrThrow()
        return group
    }

    /** The exposure this rule exists to close. */
    @Test
    fun aStrangerCannotEnumerateTheCollection() = runBlocking {
        val alice = newUser()
        val bob = newUser()
        val group = createGroup(alice, "Alice's group")
        FirebaseGroupRepositoryImpl(bob.firestore).joinGroup(group.groupId, bob.uid).getOrThrow()

        val stranger = newUser()
        val dump = runCatching {
            stranger.firestore.collection("memberships").get().await().size()
        }

        assertTrue(
            "A signed-in account in no group must not be able to read the " +
                "collection, but got $dump",
            dump.isFailure,
        )
    }

    /** Nor by pretending to belong to a group they can name. */
    @Test
    fun aStrangerCannotListTheMembersOfSomebodyElsesGroup() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, "Alice's group")

        val stranger = newUser()
        val members = runCatching {
            stranger.firestore.collection("memberships")
                .whereEqualTo("groupId", group.groupId)
                .get().await().size()
        }

        assertTrue(
            "Holding a groupId is not membership, and an invite link hands the " +
                "id to anyone, but got $members",
            members.isFailure,
        )
    }

    /** The other half: the real query still works for someone in the group. */
    @Test
    fun aMemberCanStillListTheirOwnGroupsMembers() = runBlocking {
        val alice = newUser()
        val bob = newUser()
        val group = createGroup(alice, "Shared group")
        FirebaseGroupRepositoryImpl(bob.firestore).joinGroup(group.groupId, bob.uid).getOrThrow()

        val seenByBob = FirebaseGroupRepositoryImpl(bob.firestore)
            .getGroupMembers(group.groupId)
            .first()

        assertEquals(2, seenByBob.size)
        assertTrue(seenByBob.map { it.userId }.containsAll(listOf(alice.uid, bob.uid)))
    }

    /**
     * And the other real query: listing your own memberships across groups.
     *
     * Each document this returns has a different groupId, so it exercises the
     * rule once per group rather than once overall.
     */
    @Test
    fun aUserCanStillListTheirOwnGroups() = runBlocking {
        val alice = newUser()
        val bob = newUser()
        val first = createGroup(alice, "First group")
        val second = createGroup(bob, "Second group")
        FirebaseGroupRepositoryImpl(alice.firestore).joinGroup(second.groupId, alice.uid).getOrThrow()

        val alicesGroups = FirebaseGroupRepositoryImpl(alice.firestore)
            .getUserGroups(alice.uid)
            .first()

        assertEquals(2, alicesGroups.size)
        assertTrue(
            alicesGroups.map { it.groupId }.containsAll(listOf(first.groupId, second.groupId)),
        )
    }

    /**
     * Joining must still work, which is less obvious than it sounds.
     *
     * `joinGroup()` reads the joiner's own membership before creating it, to
     * make a repeated join idempotent, so that read happens against a document
     * that does not exist and while the caller is not yet a member of anything.
     * That is why `get` stays a bare isSignedIn(): a condition touching
     * resource.data would raise an evaluation error on the missing document and
     * refuse every join.
     */
    @Test
    fun aNewcomerCanStillJoin() = runBlocking {
        val alice = newUser()
        val group = createGroup(alice, "Open group")

        val newcomer = newUser()
        val result = FirebaseGroupRepositoryImpl(newcomer.firestore)
            .joinGroup(group.groupId, newcomer.uid)

        assertTrue("A newcomer must still be able to join, got $result", result.isSuccess)
        assertEquals(
            2,
            FirebaseGroupRepositoryImpl(newcomer.firestore)
                .getGroupMembers(group.groupId)
                .first()
                .size,
        )
    }
}
