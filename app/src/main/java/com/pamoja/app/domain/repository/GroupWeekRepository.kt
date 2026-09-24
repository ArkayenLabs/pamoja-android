package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.GroupWeekSummary
import kotlinx.coroutines.flow.Flow

interface GroupWeekRepository {
    /** Newest completed week first, capped to one year by the data layer. */
    fun observeCompletedWeeks(groupId: String): Flow<List<GroupWeekSummary>>
}
