package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/** Exercises the real listener, rules and membership revocation together. */
@RunWith(AndroidJUnit4::class)
class FirebaseGroupAccessRepositoryEmulatorTest {

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

    @Test
    fun everyCurrentMemberReadsTheSameServerConfirmedAccess() = runBlocking {
        val admin = newUser()
        val member = newUser()
        val group = createGroup(admin)
        FirebaseGroupRepositoryImpl(member.firestore)
            .joinGroup(group.groupId, member.uid)
            .getOrThrow()
        seedActiveAccess(group.groupId)

        val adminAccess = confirmedAccess(admin, group.groupId)
        val memberAccess = confirmedAccess(member, group.groupId)

        assertEquals(adminAccess, memberAccess)
        assertEquals(setOf(GroupFeature.CircleV1), memberAccess.featureSet)
    }

    @Test
    fun deletingOwnMembershipClearsAnAlreadyObservedPremiumLease() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        seedActiveAccess(group.groupId)
        val repository = FirebaseGroupAccessRepositoryImpl(member.firestore)
        val activeSeen = CompletableDeferred<Unit>()

        val observations = async {
            repository.observeForCurrentMember(group.groupId, member.uid)
                .filter { it.isSuccess }
                .onEach { result ->
                    if (result.getOrNull()?.premiumAccess != null) activeSeen.complete(Unit)
                }
                .take(2)
                .toList()
        }

        withTimeout(10_000L) { activeSeen.await() }
        member.firestore.collection("memberships")
            .document("${member.uid}_${group.groupId}")
            .delete()
            .await()

        val states = withTimeout(10_000L) { observations.await() }
        assertEquals(group.groupId, states.first().getOrNull()?.premiumAccess?.groupId)
        assertNull(states.last().getOrNull()?.premiumAccess)
        assertNull(states.last().getOrNull()?.preview)
    }

    private suspend fun confirmedAccess(
        client: FirebaseEmulator.Client,
        groupId: String,
    ) = withTimeout(10_000L) {
        FirebaseGroupAccessRepositoryImpl(client.firestore)
            .observeForCurrentMember(groupId, client.uid)
            .first { result -> result.getOrNull()?.premiumAccess != null }
            .getOrThrow()
            .premiumAccess
            ?: error("Expected active access")
    }

    private suspend fun newUser(): FirebaseEmulator.Client =
        FirebaseEmulator.signIn().also { clients += it }

    private suspend fun createGroup(admin: FirebaseEmulator.Client): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "Access test group",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        FirebaseGroupRepositoryImpl(admin.firestore).createGroup(group).getOrThrow()
        return group
    }

    private fun seedActiveAccess(groupId: String) {
        FirebaseEmulator.seedServerDocument(
            collection = "groupAccess",
            documentId = groupId,
            fields = mapOf(
                "isPremium" to true,
                "featureSet" to listOf("circle_v1"),
                "leaseValidUntil" to Instant.now().plusSeconds(3_600),
            ),
        )
    }
}
