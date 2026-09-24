package com.pamoja.app.domain.model

data class AdventureEntry(val organizer: Boolean, val covered: Boolean, val adventureId: String?)
data class AdventureParticipation(val active: Boolean, val lastSyncAt: Long,
    val commitment: Long?, val revision: Long, val baselineWindowStart: Long = 0)
data class Adventure(val id: String, val target: Long, val steps: Long, val chapters: Int,
    val completed: Boolean, val earnedFinish: Boolean, val nextWeek: String, val timeZone: String,
    val plannedSteps: Long, val approximateWeeks: Long?, val awaitingMembers: Int,
    val participation: AdventureParticipation?, val serverNowMillis: Long = 0)

class AdventureFailure(val reason: String) : Exception(reason)
