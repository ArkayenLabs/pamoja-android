package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.Group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * The member cap, exercised against a real Firestore emulator.
 *
 * `CHECKLIST.md` §6.2 names this as a data-corruption risk, and it is the one
 * place in the app where two clients can race over a shared invariant. The
 * cap is enforced in two places at once: the transaction in
 * [FirebaseGroupRepositoryImpl.joinGroup] and case A of `firestore.rules`.
 * Both are live here.
 *
 * Requires the emulators, see [FirebaseEmulator].
 */
@RunWith(AndroidJUnit4::class)
class GroupJoinEmulatorTest {

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

    /** Creates a group through the real repository, so the admin membership is real too. */
    private suspend fun createGroup(admin: FirebaseEmulator.Client, cap: Int): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "Cap test group",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = cap,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        FirebaseGroupRepositoryImpl(admin.firestore).createGroup(group).getOrThrow()
        return group
    }

    private suspend fun groupDoc(client: FirebaseEmulator.Client, groupId: String) =
        client.firestore.collection("groups").document(groupId).get().await()

    private suspend fun memberCount(client: FirebaseEmulator.Client, groupId: String): Long =
        groupDoc(client, groupId).getLong("memberCount")
            ?: error("memberCount missing on group $groupId")

    @Test
    fun join_incrementsMemberCount() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin, cap = 5)
        assertEquals(1L, memberCount(admin, group.groupId))

        val joiner = newUser()
        FirebaseGroupRepositoryImpl(joiner.firestore)
            .joinGroup(group.groupId, joiner.uid)
            .getOrThrow()

        assertEquals(2L, memberCount(admin, group.groupId))
    }

    /**
     * Reopening your own invite must not count you twice.
     *
     * The membership id is deterministic, so the second write would be a
     * harmless overwrite. It is `memberCount` that would drift, permanently
     * and invisibly, until the group appeared full with empty slots.
     */
    @Test
    fun rejoining_doesNotCountTheSameMemberTwice() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin, cap = 5)
        val joiner = newUser()
        val repository = FirebaseGroupRepositoryImpl(joiner.firestore)

        repository.joinGroup(group.groupId, joiner.uid).getOrThrow()
        repository.joinGroup(group.groupId, joiner.uid).getOrThrow()
        repository.joinGroup(group.groupId, joiner.uid).getOrThrow()

        assertEquals(2L, memberCount(admin, group.groupId))
    }

    @Test
    fun joining_aFullGroup_failsWithConflict() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin, cap = 2)

        val second = newUser()
        FirebaseGroupRepositoryImpl(second.firestore)
            .joinGroup(group.groupId, second.uid)
            .getOrThrow()
        assertEquals(2L, memberCount(admin, group.groupId))

        val third = newUser()
        val result = FirebaseGroupRepositoryImpl(third.firestore)
            .joinGroup(group.groupId, third.uid)

        val error = result.exceptionOrNull()
        assertTrue(
            "A full group must fail as AppError.Conflict so the join screen can " +
                "offer a dead end rather than a Retry, but was $error",
            error is AppError.Conflict,
        )
        assertEquals(2L, memberCount(admin, group.groupId))
    }

    @Test
    fun reachingTheCap_closesTheInviteLink() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin, cap = 2)
        assertTrue(groupDoc(admin, group.groupId).getBoolean("inviteLinkActive") == true)

        val second = newUser()
        FirebaseGroupRepositoryImpl(second.firestore)
            .joinGroup(group.groupId, second.uid)
            .getOrThrow()

        assertEquals(false, groupDoc(admin, group.groupId).getBoolean("inviteLinkActive"))
    }

    /**
     * Somebody who is not joining cannot move the member count at all.
     *
     * Group ids come out of invite links and `allow get` hands one to anyone
     * signed in, so "holds a groupId" is not a privilege. Rules case A used to
     * permit any increase up to the cap, which let a stranger fill a group they
     * had never joined and shut the invite behind them. It is now pinned to +1
     * and tied, through getAfter(), to the caller's own membership existing
     * after the commit.
     *
     * Driven through raw Firestore rather than the repository on purpose: the
     * repository would never make these writes, and the rules are what has to
     * refuse them.
     */
    @Test
    fun aStrangerCannotMoveTheMemberCount() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin, cap = 5)
        val stranger = newUser()
        val strangersView = stranger.firestore.collection("groups").document(group.groupId)

        val jumpToCap = runCatching {
            strangersView.update(mapOf("memberCount" to 5, "inviteLinkActive" to false)).await()
        }
        assertTrue(
            "Filling a group you are not in must be refused, got $jumpToCap",
            jumpToCap.isFailure,
        )

        val bumpByOne = runCatching { strangersView.update("memberCount", 2).await() }
        assertTrue(
            "Even a single increment needs a membership to go with it, got $bumpByOne",
            bumpByOne.isFailure,
        )

        assertEquals(1L, memberCount(admin, group.groupId))
        assertEquals(true, groupDoc(admin, group.groupId).getBoolean("inviteLinkActive"))
    }

    /**
     * Two people joining a group that has plenty of room, at the same moment.
     *
     * Nothing is being contended for here: the cap is nowhere near, and both
     * joins are individually legal. Two people scanning the same QR code
     * together is an ordinary thing to do, so both must simply succeed and the
     * count must land on 3. If Firestore retries the loser's transaction
     * against fresh state, that is exactly what happens.
     */
    @Test
    fun twoUsersJoiningAGroupWithRoom_bothSucceed() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin, cap = 10)

        val first = newUser()
        val second = newUser()

        val results = listOf(first, second).map { joiner ->
            async(Dispatchers.IO) {
                FirebaseGroupRepositoryImpl(joiner.firestore)
                    .joinGroup(group.groupId, joiner.uid)
            }
        }.awaitAll()

        assertEquals(
            "Both joins are legal and the group has room, got $results",
            2,
            results.count { it.isSuccess },
        )
        assertEquals(3L, memberCount(admin, group.groupId))
    }

    /**
     * `CHECKLIST.md` §6.2: "Two devices joining the last slot simultaneously →
     * exactly one succeeds (transaction test)".
     *
     * This is the case the whole transaction exists for, and the only one that
     * cannot be checked by reading the code: it depends on Firestore's own
     * concurrency control. Two identities are genuinely signed in at once here,
     * so the race is real rather than two calls from one client.
     *
     * The cap holds, exactly one racer gets the slot, the count is left
     * correct, and the loser is told the group is full rather than being
     * offered a Retry that could never work.
     *
     * That last assertion is the one with history. The loser's cap check is
     * enforced by rules case A at commit, not by the check inside the
     * transaction, so the SDK reports PERMISSION_DENIED and `joinGroup()`'s own
     * Conflict is never thrown. `joinFailure()` re-reads the group and restores
     * the distinction.
     */
    @Test
    fun twoUsersRacingForTheLastSlot_exactlyOneSucceeds() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin, cap = 3)

        val second = newUser()
        FirebaseGroupRepositoryImpl(second.firestore)
            .joinGroup(group.groupId, second.uid)
            .getOrThrow()
        assertEquals(2L, memberCount(admin, group.groupId))

        val racerA = newUser()
        val racerB = newUser()

        val results = listOf(racerA, racerB).map { racer ->
            async(Dispatchers.IO) {
                FirebaseGroupRepositoryImpl(racer.firestore)
                    .joinGroup(group.groupId, racer.uid)
            }
        }.awaitAll()

        assertEquals(
            "Exactly one racer must take the last slot, got $results",
            1,
            results.count { it.isSuccess },
        )
        assertEquals(
            "The other racer must be refused rather than silently counted, got $results",
            1,
            results.count { it.isFailure },
        )
        val loserError = results.firstNotNullOf { it.exceptionOrNull() }
        assertTrue(
            "The loser must be told the group is full, not handed a retryable " +
                "error, but was $loserError",
            loserError is AppError.Conflict,
        )
        assertEquals(3L, memberCount(admin, group.groupId))
        assertEquals(false, groupDoc(admin, group.groupId).getBoolean("inviteLinkActive"))
    }
}
