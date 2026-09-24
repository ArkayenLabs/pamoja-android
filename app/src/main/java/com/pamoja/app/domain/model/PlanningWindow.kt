package com.pamoja.app.domain.model

data class PlanningWindow(
    val weekStart: String,
    val timeZone: String,
    val remainingMillis: Long,
    val currentTargetSteps: Int,
    val startDay: String,
)

/** Safe to show without paid history access. No history or response data. */
data class ScheduledGoal(
    val weekStart: String,
    val targetSteps: Int,
    val timeZone: String,
    val status: String,
)
