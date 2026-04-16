package com.pamoja.app.data.remote.model

data class StepEntryDto(
    val userId: String = "",
    val stepCount: Long = 0L,
    val date: String = ""
) {
    fun toDomain() = com.pamoja.app.domain.model.StepEntry(
        userId = userId,
        stepCount = stepCount,
        date = date
    )

    companion object {
        fun fromDomain(stepEntry: com.pamoja.app.domain.model.StepEntry) = StepEntryDto(
            userId = stepEntry.userId,
            stepCount = stepEntry.stepCount,
            date = stepEntry.date
        )
    }
}