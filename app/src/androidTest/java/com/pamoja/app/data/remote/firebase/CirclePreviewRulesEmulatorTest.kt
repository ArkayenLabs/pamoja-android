package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.Group
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/** Pins the shared, one-time Preview document behind current membership. */
@RunWith(AndroidJUnit4::class)
class CirclePreviewRulesEmulatorTest {

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
    fun everyCurrentMemberCanReadTheSamePreviewWindow() = runBlocking {
        val admin = newUser()
        val member = newUser()
        val group = createGroup(admin)
        FirebaseGroupRepositoryImpl(member.firestore)
            .joinGroup(group.groupId, member.uid)
            .getOrThrow()
        seedPreview(group.groupId)

        val adminPreview = admin.firestore.collection("groupPreviews")
            .document(group.groupId)
            .get()
            .await()
        val memberPreview = member.firestore.collection("groupPreviews")
            .document(group.groupId)
            .get()
            .await()

        assertEquals(adminPreview.getTimestamp("validUntil"), memberPreview.getTimestamp("validUntil"))
    }

    @Test
    fun aStrangerCannotReadPreviewTiming() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin)
        seedPreview(group.groupId)
        val stranger = newUser()

        val result = runCatching {
            stranger.firestore.collection("groupPreviews")
                .document(group.groupId)
                .get()
                .await()
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun noClientCanStartOrResetAPreview() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin)

        val create = runCatching {
            admin.firestore.collection("groupPreviews")
                .document(group.groupId)
                .set(mapOf("validUntil" to Instant.now().plusSeconds(99_999)))
                .await()
        }
        seedPreview(group.groupId)
        val update = runCatching {
            admin.firestore.collection("groupPreviews")
                .document(group.groupId)
                .update("validUntil", Instant.now().plusSeconds(99_999))
                .await()
        }

        assertTrue(create.isFailure)
        assertTrue(update.isFailure)
    }

    private suspend fun newUser(): FirebaseEmulator.Client =
        FirebaseEmulator.signIn().also { clients += it }

    private suspend fun createGroup(admin: FirebaseEmulator.Client): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "Preview test group",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        FirebaseGroupRepositoryImpl(admin.firestore).createGroup(group).getOrThrow()
        return group
    }

    private fun seedPreview(groupId: String) {
        FirebaseEmulator.seedServerDocument(
            "groupPreviews",
            groupId,
            mapOf(
                "schemaVersion" to 1,
                "groupId" to groupId,
                "featureSet" to listOf("circle_v1"),
                "eligibleWeekStart" to "2026-08-17",
                "startedAt" to Instant.now(),
                "validUntil" to Instant.now().plusSeconds(14 * 24 * 60 * 60L),
            ),
        )
    }
}
