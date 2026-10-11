package com.pamoja.app.data.remote.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pamoja.app.domain.model.Signal
import com.pamoja.app.domain.model.SignalKind
import com.pamoja.app.domain.repository.SignalRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseSignalRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SignalRepository {

    private fun signals(groupId: String) =
        firestore.collection("groups").document(groupId).collection("signals")

    override fun observeSent(groupId: String, userId: String, date: String): Flow<List<Signal>> =
        observe(groupId, "fromUserId", userId, date)

    override fun observeReceived(groupId: String, userId: String, date: String): Flow<List<Signal>> =
        observe(groupId, "toUserId", userId, date)

    /**
     * Both queries filter on the caller's own uid in [field], which is what
     * the list rule checks; any other shape of query is refused.
     */
    private fun observe(groupId: String, field: String, userId: String, date: String) = callbackFlow {
        val listener = signals(groupId)
            .whereEqualTo(field, userId)
            .whereEqualTo("date", date)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toFirebaseAppError())
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toSignal() })
            }
        awaitClose { listener.remove() }
    }

    override suspend fun send(
        groupId: String,
        kind: SignalKind,
        fromUserId: String,
        toUserIds: List<String>,
        date: String,
    ): Result<Unit> {
        if (toUserIds.isEmpty()) return Result.success(Unit)
        return try {
            val batch = firestore.batch()
            toUserIds.distinct().forEach { toUserId ->
                batch.set(
                    signals(groupId).document(idOf(date, kind, fromUserId, toUserId)),
                    mapOf(
                        "kind" to kind.wire,
                        "fromUserId" to fromUserId,
                        "toUserId" to toUserId,
                        "date" to date,
                        // The rules require exactly the server's clock here.
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toFirebaseAppError())
        }
    }

    companion object {
        /** The ID the rules require: one per sender, recipient, kind and day. */
        fun idOf(date: String, kind: SignalKind, fromUserId: String, toUserId: String) =
            "${date}_${kind.wire}_${fromUserId}_$toUserId"
    }
}

/** A signal document, or null when any field is missing or unknown. Also read by the data export. */
internal fun DocumentSnapshot.toSignal(): Signal? {
    val kind = SignalKind.fromWire(getString("kind").orEmpty()) ?: return null
    return Signal(
        kind = kind,
        fromUserId = getString("fromUserId") ?: return null,
        toUserId = getString("toUserId") ?: return null,
        date = getString("date") ?: return null,
    )
}
