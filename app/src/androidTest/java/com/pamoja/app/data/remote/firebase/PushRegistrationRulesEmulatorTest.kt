package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Pins the server-write/owner-export boundary for FCM installation IDs. */
@RunWith(AndroidJUnit4::class)
class PushRegistrationRulesEmulatorTest {

    private val clients = mutableListOf<FirebaseEmulator.Client>()

    @Before
    fun wipeEmulators() = FirebaseEmulator.reset()

    @After
    fun closeClients() {
        clients.forEach { it.close() }
        clients.clear()
    }

    private suspend fun newUser(): FirebaseEmulator.Client =
        FirebaseEmulator.signIn().also { clients += it }

    @Test
    fun registrationsAreServerWrittenAndReadableOnlyByTheirOwner() = runBlocking {
        val owner = newUser()
        val other = newUser()
        seed("fid-owner", owner.uid)
        seed("fid-other", other.uid)

        val result = owner.firestore.collection("pushRegistrations")
            .whereEqualTo("userId", owner.uid)
            .get()
            .await()

        assertEquals(listOf("fid-owner"), result.documents.map { it.id })

        val attempt = runCatching {
            other.firestore.collection("pushRegistrations")
                .document("fid-owner")
                .get()
                .await()
        }

        assertTrue(attempt.isFailure)

        val registrations = owner.firestore.collection("pushRegistrations")

        val create = runCatching {
            registrations.document("fid-forged")
                .set(mapOf("userId" to owner.uid))
                .await()
        }
        val update = runCatching {
            registrations.document("fid-owner")
                .update("appVersion", "forged")
                .await()
        }
        val delete = runCatching {
            registrations.document("fid-owner").delete().await()
        }

        assertTrue(create.isFailure)
        assertTrue(update.isFailure)
        assertTrue(delete.isFailure)
    }

    private fun seed(installationId: String, userId: String) {
        FirebaseEmulator.seedServerDocument(
            collection = "pushRegistrations",
            documentId = installationId,
            fields = mapOf(
                "installationId" to installationId,
                "userId" to userId,
                "platform" to "android",
                "appVersion" to "1.0.0",
                "updatedAt" to Instant.parse("2026-09-03T00:00:00Z"),
            ),
        )
    }
}
