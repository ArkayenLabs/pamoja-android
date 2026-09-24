package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.repository.GroupWeekRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class ObserveGroupWeekSummariesUseCase @Inject constructor(
    private val groupWeekRepository: GroupWeekRepository,
) {
    operator fun invoke(groupId: String): Flow<List<GroupWeekSummary>> =
        if (groupId.isBlank()) flowOf(emptyList())
        else groupWeekRepository.observeCompletedWeeks(groupId)
}
