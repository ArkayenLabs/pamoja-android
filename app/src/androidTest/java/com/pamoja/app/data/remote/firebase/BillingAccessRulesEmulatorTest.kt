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
import java.util.UUID

/**
 * Pins the client/server trust boundary for paid group access.
 *
 * The Functions backend owns every write. Group members may read only the
 * non-sensitive group projection, while a payer may read only their personal
 * projection. Canonical RevenueCat and sponsor records are never client data.
 */
@RunWith(AndroidJUnit4::class)
class BillingAccessRulesEmulatorTest {

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
            name = "Sponsored circle",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        FirebaseGroupRepositoryImpl(admin.firestore).createGroup(group).getOrThrow()
        return group
    }

    @Test
    fun aGroupMemberCanReadThePublicAccessProjection() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        FirebaseEmulator.seedServerDocument(
            collection = "groupAccess",
            documentId = group.groupId,
            fields = mapOf(
                "isPremium" to true,
                "featureSet" to listOf("circle_v1"),
            ),
        )

        val access = member.firestore.collection("groupAccess")
            .document(group.groupId)
            .get()
            .await()

        assertEquals(true, access.getBoolean("isPremium"))
    }

    @Test
    fun aStrangerCannotReadAnotherGroupsAccessProjection() = runBlocking {
        val member = newUser()
        val group = createGroup(member)
        FirebaseEmulator.seedServerDocument(
            "groupAccess",
            group.groupId,
            mapOf("isPremium" to true),
        )
        val stranger = newUser()

        val result = runCatching {
            stranger.firestore.collection("groupAccess")
                .document(group.groupId)
                .get()
                .await()
        }

        assertTrue("A group ID alone must not reveal paid status", result.isFailure)
    }

    @Test
    fun noClientCanMintGroupAccess() = runBlocking {
        val member = newUser()
        val group = createGroup(member)

        val result = runCatching {
            member.firestore.collection("groupAccess")
                .document(group.groupId)
                .set(mapOf("isPremium" to true))
                .await()
        }

        assertTrue("Even the group admin must not mint premium access", result.isFailure)
    }

    @Test
    fun aPayerCanReadOnlyTheirOwnBillingView() = runBlocking {
        val payer = newUser()
        val otherUser = newUser()
        FirebaseEmulator.seedServerDocument(
            "billingViews",
            payer.uid,
            mapOf("isEntitled" to true),
        )

        val ownView = payer.firestore.collection("billingViews")
            .document(payer.uid)
            .get()
            .await()
        val someoneElsesRead = runCatching {
            otherUser.firestore.collection("billingViews")
                .document(payer.uid)
                .get()
                .await()
        }

        assertEquals(true, ownView.getBoolean("isEntitled"))
        assertTrue(someoneElsesRead.isFailure)
    }

    @Test
    fun canonicalBillingRecordsAreServerOnly() = runBlocking {
        val payer = newUser()
        val group = createGroup(payer)
        val documents = listOf(
            "billingAccounts" to payer.uid,
            "groupBilling" to group.groupId,
            "revenueCatEvents" to "event_123",
        )
        documents.forEach { (collection, documentId) ->
            FirebaseEmulator.seedServerDocument(
                collection,
                documentId,
                mapOf("status" to "active"),
            )
        }

        documents.forEach { (collection, documentId) ->
            val result = runCatching {
                payer.firestore.collection(collection)
                    .document(documentId)
                    .get()
                    .await()
            }
            assertTrue("$collection must be server-only", result.isFailure)
        }
    }
}
