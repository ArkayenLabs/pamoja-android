package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.Signal
import com.pamoja.app.domain.model.SignalKind
import kotlinx.coroutines.flow.Flow

/** Cheers and nudges within one group. See firestore.rules, `signals`. */
interface SignalRepository {
    /** What [userId] sent in [groupId] on [date]: fills the hearts and "Nudged". */
    fun observeSent(groupId: String, userId: String, date: String): Flow<List<Signal>>

    /** What [userId] received in [groupId] on [date]: the cheer card. */
    fun observeReceived(groupId: String, userId: String, date: String): Flow<List<Signal>>

    /**
     * Sends one [kind] from [fromUserId] to each of [toUserIds], all or none.
     * Each recipient must not have had one of this kind from this sender on
     * [date] already, or the whole write is refused.
     */
    suspend fun send(
        groupId: String,
        kind: SignalKind,
        fromUserId: String,
        toUserIds: List<String>,
        date: String,
    ): Result<Unit>
}
