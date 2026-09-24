package com.pamoja.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pamoja.app.data.remote.model.GroupWeekSummaryDto
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.repository.GroupWeekRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class FirebaseGroupWeekRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : GroupWeekRepository {

    override fun observeCompletedWeeks(groupId: String): Flow<List<GroupWeekSummary>> {
        require(groupId.isNotBlank()) { "groupId must not be blank" }

        return callbackFlow {
            val listener = firestore
                .collection("groups")
                .document(groupId)
                .collection("weeks")
                .orderBy("weekStart", Query.Direction.DESCENDING)
                .limit(MAX_COMPLETED_WEEKS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error.toFirebaseAppError())
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener

                    trySend(
                        snapshot.documents.mapNotNull { document ->
                            document.toObject(GroupWeekSummaryDto::class.java)?.toDomain()
                        },
                    )
                }
            awaitClose { listener.remove() }
        }
    }

    companion object {
        private const val MAX_COMPLETED_WEEKS = 52L
    }
}
