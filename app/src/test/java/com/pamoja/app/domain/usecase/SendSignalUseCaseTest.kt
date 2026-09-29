package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.Signal
import com.pamoja.app.domain.model.SignalKind
import com.pamoja.app.domain.repository.SignalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SendSignalUseCaseTest {

    private class RecordingSignals : SignalRepository {
        val sent = mutableListOf<List<String>>()
        override fun observeSent(groupId: String, userId: String, date: String): Flow<List<Signal>> = emptyFlow()
        override fun observeReceived(groupId: String, userId: String, date: String): Flow<List<Signal>> = emptyFlow()
        override suspend fun send(
            groupId: String,
            kind: SignalKind,
            fromUserId: String,
            toUserIds: List<String>,
            date: String,
        ): Result<Unit> {
            sent += toUserIds
            return Result.success(Unit)
        }
    }

    /** The rules refuse a whole batch over one repeat, so a repeat must never reach them. */
    @Test
    fun leavesOutYourselfAndAnyoneAlreadySentThisKindToday() = runBlocking {
        val signals = RecordingSignals()
        val today = "2026-10-01"

        SendSignalUseCase(signals)(
            groupId = "g",
            kind = SignalKind.NUDGE,
            fromUserId = "me",
            toUserIds = listOf("me", "arjun", "meera", "arjun", ""),
            date = today,
            alreadySent = listOf(
                Signal(SignalKind.NUDGE, "me", "meera", today),
                // A cheer is not a nudge, and yesterday is not today.
                Signal(SignalKind.CHEER, "me", "arjun", today),
                Signal(SignalKind.NUDGE, "me", "arjun", "2026-09-30"),
            ),
        )

        assertEquals(listOf(listOf("arjun")), signals.sent)
    }
}
