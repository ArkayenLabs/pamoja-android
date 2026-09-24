package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Query
import com.pamoja.app.domain.model.Group
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.time.Instant

/** Pins privacy and immutability for server-finalized weekly history. */
@RunWith(AndroidJUnit4::class)
class GroupWeekRulesEmulatorTest {

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

    private suspend fun createGroup(admin: FirebaseEmulator.Client): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "History circle",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        FirebaseGroupRepositoryImpl(admin.firestore).createGroup(group).getOrThrow()
        return group
    }

    private fun seedWeek(groupId: String) {
        FirebaseEmulator.seedServerDocumentPath(
            "groups/$groupId/weeks/2026-08-17",
            mapOf(
                "schemaVersion" to 1,
                "groupId" to groupId,
                "weekStart" to "2026-08-17",
                "weekEnd" to "2026-08-23",
                "targetSteps" to 70_000L,
                "totalSteps" to 84_000L,
                "memberCount" to 1,
                "activeMemberCount" to 1,
            ),
        )
    }

    private fun seedPreview(groupId: String, validUntil: Instant) {
        FirebaseEmulator.seedServerDocument(
            "groupPreviews",
            groupId,
            mapOf(
                "schemaVersion" to 1,
                "groupId" to groupId,
                "featureSet" to listOf("circle_v1"),
                "eligibleWeekStart" to "2026-08-17",
                "startedAt" to Instant.now().minusSeconds(60),
                "validUntil" to validUntil,
            ),
        )
    }

    @Test
    fun aCurrentMemberCanReadAndListCompletedWeeks() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        seedWeek(group.groupId)
        seedPreview(group.groupId, Instant.now().plusSeconds(3_600))

        val document = member.firestore.collection("groups")
            .document(group.groupId)
            .collection("weeks")
            .document("2026-08-17")
            .get()
            .await()
        val query = member.firestore.collection("groups")
            .document(group.groupId)
            .collection("weeks")
            .orderBy("weekStart", Query.Direction.DESCENDING)
            .get()
            .await()

        assertEquals(84_000L, document.getLong("totalSteps"))
        assertEquals(1, query.size())
    }

    @Test
    fun aCurrentMemberWithoutPreviewOrPaidAccessCannotReadHistory() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        seedWeek(group.groupId)

        val result = runCatching {
            member.firestore.collection("groups")
                .document(group.groupId)
                .collection("weeks")
                .document("2026-08-17")
                .get()
                .await()
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun anExpiredPreviewCannotReadHistory() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        seedWeek(group.groupId)
        seedPreview(group.groupId, Instant.now().minusSeconds(60))

        val result = runCatching {
            member.firestore.collection("groups")
                .document(group.groupId)
                .collection("weeks")
                .document("2026-08-17")
                .get()
                .await()
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun aFutureDatedPreviewCannotUnlockHistoryEarly() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        seedWeek(group.groupId)
        FirebaseEmulator.seedServerDocument(
            "groupPreviews",
            group.groupId,
            mapOf(
                "schemaVersion" to 1,
                "groupId" to group.groupId,
                "featureSet" to listOf("circle_v1"),
                "eligibleWeekStart" to "2026-08-17",
                "startedAt" to Instant.now().plusSeconds(3_600),
                "validUntil" to Instant.now().plusSeconds(7_200),
            ),
        )

        val result = runCatching {
            member.firestore.collection("groups")
                .document(group.groupId)
                .collection("weeks")
                .document("2026-08-17")
                .get()
                .await()
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun anOverlongPreviewCannotUnlockHistory() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        seedWeek(group.groupId)
        FirebaseEmulator.seedServerDocument(
            "groupPreviews",
            group.groupId,
            mapOf(
                "schemaVersion" to 1,
                "groupId" to group.groupId,
                "featureSet" to listOf("circle_v1"),
                "eligibleWeekStart" to "2026-08-17",
                "startedAt" to Instant.now().minusSeconds(60),
                "validUntil" to Instant.now().plusSeconds(15 * 24 * 60 * 60L),
            ),
        )

        val result = runCatching {
            member.firestore.collection("groups")
                .document(group.groupId)
                .collection("weeks")
                .document("2026-08-17")
                .get()
                .await()
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun aServerConfirmedPaidLeaseCanReadHistoryAfterPreviewEnds() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        seedWeek(group.groupId)
        seedPreview(group.groupId, Instant.now().minusSeconds(60))
        FirebaseEmulator.seedServerDocument(
            "groupAccess",
            group.groupId,
            mapOf(
                "isPremium" to true,
                "featureSet" to listOf("circle_v1"),
                "leaseValidUntil" to Instant.now().plusSeconds(3_600),
            ),
        )

        val document = member.firestore.collection("groups")
            .document(group.groupId)
            .collection("weeks")
            .document("2026-08-17")
            .get()
            .await()

        assertEquals(84_000L, document.getLong("totalSteps"))
    }

    @Test
    fun anInviteHolderWhoNeverJoinedCannotReadHistory() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        seedWeek(group.groupId)
        seedPreview(group.groupId, Instant.now().plusSeconds(3_600))
        val stranger = newUser()

        val result = runCatching {
            stranger.firestore.collection("groups")
                .document(group.groupId)
                .collection("weeks")
                .document("2026-08-17")
                .get()
                .await()
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun evenTheGroupAdminCannotWriteACompletedWeek() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin)

        val result = runCatching {
            admin.firestore.collection("groups")
                .document(group.groupId)
                .collection("weeks")
                .document("2026-08-17")
                .set(mapOf("totalSteps" to 99_999_999L))
                .await()
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun deletingAGroupAlsoClosesReadAccessToItsOrphanedHistory() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin)
        seedWeek(group.groupId)
        seedPreview(group.groupId, Instant.now().plusSeconds(3_600))

        admin.firestore.collection("groups").document(group.groupId).delete().await()
        val result = runCatching {
            admin.firestore.collection("groups")
                .document(group.groupId)
                .collection("weeks")
                .document("2026-08-17")
                .get()
                .await()
        }

        assertTrue(result.isFailure)
    }
}
