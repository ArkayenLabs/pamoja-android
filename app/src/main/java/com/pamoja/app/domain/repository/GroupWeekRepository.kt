package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.GroupWeekSummary
import kotlinx.coroutines.flow.Flow

interface GroupWeekRepository {
    /** Newest completed week first, capped to one year by the data layer. */
    fun observeCompletedWeeks(groupId: String): Flow<List<GroupWeekSummary>>

    /**
     * One finished week, or null if it has not been finalized. A single get,
     * which is what the rules allow a free member for the last finished week.
     */
    fun observeWeek(groupId: String, weekStart: String): Flow<GroupWeekSummary?>
}
