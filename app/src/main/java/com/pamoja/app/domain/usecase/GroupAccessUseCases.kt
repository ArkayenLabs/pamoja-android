package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupAccessSnapshot
import com.pamoja.app.domain.model.GroupFeature
import com.pamoja.app.domain.model.PamojaGroupId
import com.pamoja.app.domain.repository.GroupAccessRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import java.time.Clock
import javax.inject.Inject

/** Converts an untrusted server projection into the state paid gates consume. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveGroupAccessUseCase @Inject constructor(
    private val repository: GroupAccessRepository,
    private val clock: Clock,
) {
    operator fun invoke(groupId: String, userId: String): Flow<GroupAccessState> {
        val normalizedGroupId = groupId.trim()
        val normalizedUserId = userId.trim()
        if (!PamojaGroupId.isValid(normalizedGroupId) || normalizedUserId.isBlank()) {
            return flowOf(GroupAccessState.Loading, GroupAccessState.Free)
        }

        return repository
            .observeForCurrentMember(normalizedGroupId, normalizedUserId)
            .transformLatest { result ->
                val snapshot = result.getOrElse { error ->
                    emit(GroupAccessState.Unavailable(error.toAppError()))
                    return@transformLatest
                }

                var effectiveNow = clock.millis()
                while (true) {
                    val state = snapshot.stateAt(effectiveNow)
                    emit(state)
                    val nextExpiry = state.activeUntilMillisOrNull() ?: break
                    val remainingMillis = nextExpiry - effectiveNow
                    if (remainingMillis <= 0L) break
                    delay(remainingMillis)
                    effectiveNow = nextExpiry
                }
            }
            .onStart { emit(GroupAccessState.Loading) }
    }
}

private fun GroupAccessSnapshot.stateAt(nowMillis: Long): GroupAccessState {
    val activePaid = premiumAccess?.takeIf { access ->
        access.includes(GroupFeature.CircleV1) && access.validUntilMillis > nowMillis
    }
    if (activePaid != null) return GroupAccessState.Premium(activePaid)

    val knownPreview = preview
    if (knownPreview != null) {
        val isActive = knownPreview.includes(GroupFeature.CircleV1) &&
            knownPreview.startedAtMillis <= nowMillis &&
            knownPreview.validUntilMillis > nowMillis
        return if (isActive) {
            GroupAccessState.Preview(knownPreview)
        } else {
            GroupAccessState.PreviewExpired(knownPreview)
        }
    }
    return GroupAccessState.Free
}

private fun GroupAccessState.activeUntilMillisOrNull(): Long? = when (this) {
    is GroupAccessState.Premium -> access.validUntilMillis
    is GroupAccessState.Preview -> preview.validUntilMillis
    else -> null
}
