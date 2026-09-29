package com.pamoja.app.widgets

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.WalkedDays
import com.pamoja.app.domain.model.WeekWindow
import java.time.Clock
import java.time.LocalDate

/**
 * What the widgets last learned, stored as it was read. Every "is this still
 * today / this week" check happens when a widget is drawn, not when this was
 * written: a widget redraws on its own schedule, and a snapshot from before
 * midnight must not show yesterday's walks as today's.
 */
data class WidgetSnapshot(
    val userId: String,
    /** The group Home shows; null when the user is in none. */
    val group: Group?,
    val members: List<Membership> = emptyList(),
    /** The day [cheersFrom], [cheeredBack] and [nudged] belong to. */
    val signalsDate: String = "",
    /** Who cheered this user today, in the order they arrived. */
    val cheersFrom: List<String> = emptyList(),
    /** Whom this user cheered today. */
    val cheeredBack: Set<String> = emptySet(),
    /** Whom this user nudged today. */
    val nudged: Set<String> = emptySet(),
    /** This user's steps on [stepsDate] from Health Connect; null without access. */
    val todaySteps: Long? = null,
    val stepsDate: String = "",
    val usualSteps: Long? = null,
    /** When this was read, in epoch millis. */
    val refreshedAt: Long = 0L,
)

data class WidgetFace(
    val userId: String,
    val name: String,
    val photoUrl: String,
    val walked: Boolean,
    val isYou: Boolean,
)

/** The group card, as drawn in the mockup's row B. */
data class GroupCardModel(
    val groupId: String,
    val groupName: String,
    /** Weeks in a row, or 0 when there is no streak worth a chip (under two). */
    val streak: Int,
    /** Up to [MAX_FACES]; when the group is bigger the last slot becomes [overflow]. */
    val faces: List<WidgetFace>,
    val overflow: Int,
    val steps: Long,
    val target: Long,
    val daysLeft: Int,
    /** First names of the people who have not walked yet, shown once somebody has. */
    val waiting: List<String>,
    /** Of those, the ones not nudged yet today. Empty means "Nudged". */
    val toNudge: List<String>,
) {
    val progressPercent: Int
        get() = if (target <= 0) 0 else ((steps * 100) / target).coerceIn(0, 100).toInt()
    val reached: Boolean get() = target in 1..steps

    companion object {
        const val MAX_FACES = 6
    }
}

sealed interface YouTodayModel {
    /** Somebody cheered today: the tile turns lime, as on Home. */
    data class Cheered(
        val from: WidgetFace,
        /** Other people who cheered too. */
        val others: Int,
        /** Cheerers not cheered back yet. Empty means "Cheered". */
        val toCheerBack: List<String>,
    ) : YouTodayModel

    /** [steps] null means the last figure is from another day. */
    data class Steps(val steps: Long?, val usual: Long?) : YouTodayModel

    /** No Health Connect access, so nothing to count. */
    data object Connect : YouTodayModel
}

sealed interface StreakModel {
    data class Streak(val weeks: Int) : StreakModel

    /** No streak of two yet: this week's share of the goal instead. */
    data class Progress(val percent: Int) : StreakModel

    data object NoGroup : StreakModel
}

/** The same one-thousand-step walk Home ticks a face for. */
private fun Membership.walkedOn(today: String): Boolean =
    todayDate == today && todaySteps >= WalkedDays.MIN_STEPS

private fun Membership.weekFigure(week: String): Long = weeklySteps.takeIf { weekStart == week } ?: 0L

private fun firstName(name: String): String = name.trim().substringBefore(' ').ifBlank { name }

fun groupCard(snapshot: WidgetSnapshot, clock: Clock = Clock.systemDefaultZone()): GroupCardModel? {
    val group = snapshot.group ?: return null
    val todayText = LocalDate.now(clock).toString()
    val reporting = WeekWindow.todayFor(group, clock)
    val week = WeekWindow.startOf(group.startDay, reporting)
    val steps = snapshot.members.sumOf { it.weekFigure(week) }
    val target = group.weeklyTarget.toLong()
    val reached = target in 1..steps
    val streak = group.streakFor(week) + if (reached) 1 else 0

    // Most steps today first, as on Home.
    val ordered = snapshot.members.sortedByDescending { if (it.todayDate == todayText) it.todaySteps else 0L }
    val faces = ordered.map {
        WidgetFace(
            userId = it.userId,
            name = it.displayName,
            photoUrl = it.photoUrl,
            walked = it.walkedOn(todayText),
            isYou = it.userId == snapshot.userId,
        )
    }
    val shown = if (faces.size > GroupCardModel.MAX_FACES) faces.take(GroupCardModel.MAX_FACES - 1) else faces

    // Only once somebody has walked: at seven in the morning a list of
    // everyone is not a nudge, it is an alarm clock. Same rule as Home.
    val others = faces.filter { !it.isYou && !it.walked }
    val anyoneWalked = faces.any { it.walked }
    val waiting = if (anyoneWalked) others else emptyList()
    val nudgedToday = snapshot.nudged.takeIf { snapshot.signalsDate == todayText }.orEmpty()

    return GroupCardModel(
        groupId = group.groupId,
        groupName = group.name,
        streak = streak.takeIf { it >= 2 } ?: 0,
        faces = shown,
        overflow = faces.size - shown.size,
        steps = steps,
        target = target,
        daysLeft = WeekWindow.daysLeftIn(group.startDay, reporting),
        waiting = waiting.map { firstName(it.name) },
        toNudge = waiting.map { it.userId }.filterNot { it in nudgedToday },
    )
}

fun youToday(snapshot: WidgetSnapshot, clock: Clock = Clock.systemDefaultZone()): YouTodayModel {
    val todayText = LocalDate.now(clock).toString()
    if (snapshot.signalsDate == todayText) {
        val members = snapshot.members.associateBy { it.userId }
        val cheerers = snapshot.cheersFrom.distinct().mapNotNull { members[it] }.filter { it.userId != snapshot.userId }
        cheerers.firstOrNull()?.let { first ->
            return YouTodayModel.Cheered(
                from = WidgetFace(first.userId, first.displayName, first.photoUrl, walked = false, isYou = false),
                others = cheerers.size - 1,
                toCheerBack = cheerers.map { it.userId }.filterNot { it in snapshot.cheeredBack },
            )
        }
    }
    if (snapshot.todaySteps == null) return YouTodayModel.Connect
    return YouTodayModel.Steps(
        steps = snapshot.todaySteps.takeIf { snapshot.stepsDate == todayText },
        usual = snapshot.usualSteps,
    )
}

fun streakTile(snapshot: WidgetSnapshot, clock: Clock = Clock.systemDefaultZone()): StreakModel {
    val card = groupCard(snapshot, clock) ?: return StreakModel.NoGroup
    return if (card.streak >= 2) StreakModel.Streak(card.streak) else StreakModel.Progress(card.progressPercent)
}
