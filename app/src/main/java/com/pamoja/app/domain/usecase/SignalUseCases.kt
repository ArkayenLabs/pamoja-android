package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.Signal
import com.pamoja.app.domain.model.SignalKind
import com.pamoja.app.domain.repository.SignalRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSentSignalsUseCase @Inject constructor(
    private val signalRepository: SignalRepository,
) {
    operator fun invoke(groupId: String, userId: String, date: String): Flow<List<Signal>> =
        signalRepository.observeSent(groupId, userId, date)
}

class ObserveReceivedSignalsUseCase @Inject constructor(
    private val signalRepository: SignalRepository,
) {
    operator fun invoke(groupId: String, userId: String, date: String): Flow<List<Signal>> =
        signalRepository.observeReceived(groupId, userId, date)
}

/**
 * Sends a cheer or a nudge to each of [toUserIds].
 *
 * Leaves out the sender and anyone already sent this kind today, since the
 * rules refuse the whole batch over one repeat and a double tap is the most
 * likely way to produce one.
 */
class SendSignalUseCase @Inject constructor(
    private val signalRepository: SignalRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        kind: SignalKind,
        fromUserId: String,
        toUserIds: List<String>,
        date: String,
        alreadySent: Collection<Signal> = emptyList(),
    ): Result<Unit> {
        val done = alreadySent.filter { it.kind == kind && it.date == date }.map { it.toUserId }.toSet()
        val recipients = toUserIds.filter { it.isNotBlank() && it != fromUserId && it !in done }.distinct()
        return signalRepository.send(groupId, kind, fromUserId, recipients, date)
    }
}
