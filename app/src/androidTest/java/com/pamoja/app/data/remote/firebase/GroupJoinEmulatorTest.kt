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
     * `CHECKLIST.md` §6.2: "Two devices joining the last slot simultaneously →
     * exactly one succeeds (transaction test)".
     *
     * This is the case the whole transaction exists for, and the only one that
     * cannot be checked by reading the code: it depends on Firestore's own
     * concurrency control. Two identities are genuinely signed in at once here,
     * so the race is real rather than two calls from one client.
     *
     * What is asserted is the invariant that matters: the cap holds, exactly
     * one racer gets the slot, and the count is left correct.
     *
     * Deliberately NOT asserted: which [AppError] the loser receives. Observed
     * behaviour is [AppError.PermissionDenied], not [AppError.Conflict], and
     * that is a real gap rather than a quirk of the test. Both racers read
     * `memberCount = 2` and pass the in-transaction cap check, so both try to
     * write 3. The winner commits; at the loser's commit `resource.data` is
     * already 3, so rules case A fails `memberCount > resource.data.memberCount`
     * and Firestore rejects it outright instead of aborting for contention and
     * retrying. The repository's own `Conflict` is therefore never reached.
     * The join screen keys its "group is full" dead end off `Conflict`, so a
     * user who loses this race sees a generic, retryable error instead.
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
        assertEquals(3L, memberCount(admin, group.groupId))
        assertEquals(false, groupDoc(admin, group.groupId).getBoolean("inviteLinkActive"))
    }
}
