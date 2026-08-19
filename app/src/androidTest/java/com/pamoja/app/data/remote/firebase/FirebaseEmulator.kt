package com.pamoja.app.data.remote.firebase

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.tasks.await
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wiring for the emulator-backed integration tests.
 *
 * These tests use no mocks and no fakes. Every call goes to a real Firestore
 * emulator loading this repository's real `firestore.rules`, signed in as real
 * Firebase Auth users. A rules change that would break a join on a device
 * breaks these tests too, which is the entire point: the invariants under test
 * are enforced jointly by the transaction in the repository and by the rules,
 * and testing either half alone proves very little.
 *
 * Prerequisite, from the repository root:
 *
 *     firebase emulators:start --only auth,firestore
 *
 * `10.0.2.2` is the host machine as seen from inside an Android emulator.
 * Cleartext to it is permitted by the debug-only network security config.
 */
object FirebaseEmulator {

    const val HOST = "10.0.2.2"
    const val FIRESTORE_PORT = 8080
    const val AUTH_PORT = 9099

    private val counter = AtomicInteger(0)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Reads the real project id rather than hardcoding it. */
    val projectId: String
        get() = defaultApp().options.projectId
            ?: error("No projectId in FirebaseOptions, is google-services.json present?")

    private fun defaultApp(): FirebaseApp =
        runCatching { FirebaseApp.getInstance() }
            .getOrElse { FirebaseApp.initializeApp(context) ?: error("Firebase init failed") }

    /**
     * One signed-in Firebase client, isolated from every other.
     *
     * Each client gets its own [FirebaseApp], because a single instance can
     * only ever hold one identity at a time. Two identities have to coexist for
     * the concurrent-join test to mean anything: it is precisely two different
     * users racing for the same slot.
     *
     * The API key and application id are copied from the default app, so no
     * credential is duplicated into source. `google-services.json` is
     * gitignored and stays the only place they live.
     */
    class Client(
        val uid: String,
        val firestore: FirebaseFirestore,
        /** Exposed so tests can build the real AuthRepository rather than a stand-in. */
        val auth: FirebaseAuth,
        private val app: FirebaseApp,
    ) {
        fun close() = app.delete()
    }

    suspend fun signIn(): Client {
        val name = "emulator-test-${counter.incrementAndGet()}"
        val app = FirebaseApp.initializeApp(context, defaultApp().options, name)
            ?: error("Could not create secondary FirebaseApp '$name'")

        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
        val firestore = FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, FIRESTORE_PORT)
            // Off deliberately. The local cache would answer reads from a
            // previous test after the emulator had been wiped, so a test could
            // pass against data that no longer exists on the server.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }

        val email = "user-${counter.get()}-${System.nanoTime()}@pamoja.test"
        val created = auth.createUserWithEmailAndPassword(email, "test-password-123").await()
        val uid = created.user?.uid ?: error("Auth emulator returned no uid")

        return Client(uid, firestore, auth, app)
    }

    /**
     * Wipes both emulators so each test starts from nothing.
     *
     * Uses the emulators' own admin endpoints, which bypass security rules.
     * Doing it through the SDK would need a delete permitted by the rules, and
     * the rules are one of the things under test.
     */
    fun reset() {
        delete("http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$projectId/databases/(default)/documents")
        delete("http://$HOST:$AUTH_PORT/emulator/v1/projects/$projectId/accounts")
    }

    private fun delete(url: String) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "DELETE"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val code = connection.responseCode
            check(code in 200..299) { "Emulator reset failed ($code) for $url" }
        } finally {
            connection.disconnect()
        }
    }
}
