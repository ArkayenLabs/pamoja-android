package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.repository.GroupWeekRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** The group's last finished week: free to every member, unlike the rest. */
class ObserveLastFinishedWeekUseCase @Inject constructor(
    private val groupWeekRepository: GroupWeekRepository,
) {
    operator fun invoke(group: Group): Flow<GroupWeekSummary?> =
        if (group.groupId.isBlank() || group.streakThroughWeek.isBlank()) flowOf(null)
        else groupWeekRepository.observeWeek(group.groupId, group.streakThroughWeek)
}

class ObserveGroupWeekSummariesUseCase @Inject constructor(
    private val groupWeekRepository: GroupWeekRepository,
) {
    operator fun invoke(groupId: String): Flow<List<GroupWeekSummary>> =
        if (groupId.isBlank()) flowOf(emptyList())
        else groupWeekRepository.observeCompletedWeeks(groupId)
}
