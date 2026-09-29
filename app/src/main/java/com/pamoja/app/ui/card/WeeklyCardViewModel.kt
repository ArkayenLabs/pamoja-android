package com.pamoja.app.ui.card

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.domain.analytics.AnalyticsEvent
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.analytics.AnalyticsParam
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.AwardCandidate
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.model.WeeklyAward
import com.pamoja.app.domain.model.WeeklyAwards
import com.pamoja.app.domain.usecase.GetGroupMembershipsUseCase
import com.pamoja.app.domain.usecase.GetGroupUseCase
import com.pamoja.app.domain.usecase.ObserveLastFinishedWeekUseCase
import com.pamoja.app.domain.model.GroupWeekSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class CardMember(
    val userId: String,
    val name: String,
    val photoUrl: String?,
    val weeklySteps: Long,
)

data class WeeklyCardUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val weekStart: LocalDate? = null,
    /** Most steps first. */
    val members: List<CardMember> = emptyList(),
    val total: Long = 0L,
    val reached: Boolean = false,
    /** Weeks in a row including this one once its goal is met; shown from 2. */
    val streak: Int = 0,
    val walks: Int = 0,
    val awards: List<Pair<CardMember, WeeklyAward>> = emptyList(),
    val error: AppError? = null,
    /** A finished week (the last one), rather than this week so far. */
    val finished: Boolean = false,
    /** Whether a last-week card exists to switch to. */
    val hasLastWeek: Boolean = false,
)

/**
 * This week so far, as a picture to share (REBUILD_PLAN.md Phase 5).
 *
 * Built only from what Home already reads, the group and its memberships, so
 * it needs no new data, no rules change and no Premium history. A card for a
 * finished week can come later as a second mode beside this one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeeklyCardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupMembershipsUseCase: GetGroupMembershipsUseCase,
    private val observeLastFinishedWeekUseCase: ObserveLastFinishedWeekUseCase,
    private val analyticsManager: AnalyticsManager,
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()

    private val thisWeek = MutableStateFlow(WeeklyCardUiState())
    private val lastWeek = MutableStateFlow<WeeklyCardUiState?>(null)
    private val showLastWeek = MutableStateFlow(false)

    /** This week so far, or the last finished week when chosen and available. */
    val uiState: StateFlow<WeeklyCardUiState> =
        combine(thisWeek, lastWeek, showLastWeek) { current, last, showLast ->
            (if (showLast && last != null) last else current).copy(hasLastWeek = last != null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyCardUiState())

    init {
        val group = getGroupUseCase.observe(groupId)
        val memberships = getGroupMembershipsUseCase(groupId)
        viewModelScope.launch {
            combine(group, memberships) { g, m -> weeklyCardOf(g, m) }
                .catch { error -> thisWeek.value = thisWeek.value.copy(isLoading = false, error = error.toAppError()) }
                .collect { thisWeek.value = it }
        }
        // The last finished week is free to every member, so this card is too.
        viewModelScope.launch {
            group.distinctUntilChanged { a, b -> a.streakThroughWeek == b.streakThroughWeek }
                .flatMapLatest { g ->
                    combine(observeLastFinishedWeekUseCase(g), memberships) { week, m ->
                        week?.let { finishedWeekCardOf(g, it, m) }
                    }
                }
                .catch { emit(null) }
                .collect { lastWeek.value = it }
        }
    }

    fun showLastWeek(show: Boolean) {
        showLastWeek.value = show
    }

    fun onShared() {
        analyticsManager.logEvent(AnalyticsEvent.WEEKLY_CARD_SHARED, mapOf(AnalyticsParam.GROUP_ID to groupId))
    }
}

/**
 * A finished week from its server-finalized record. The total is the record's
 * own, former members included; the faces and lineup name only people still
 * in the group, and a user ID is never shown in place of a name.
 */
internal fun finishedWeekCardOf(group: Group, week: GroupWeekSummary, memberships: List<Membership>): WeeklyCardUiState {
    val names = memberships.associateBy { it.userId }
    val named = week.contributions.mapNotNull { c -> names[c.userId]?.let { it to c } }
    val candidates = named.map { (_, c) -> AwardCandidate(c.userId, c.stepCount, c.walkedDays) }
    val members = named.map { (m, c) ->
        CardMember(
            userId = c.userId,
            name = m.displayName.trim().substringBefore(' ').ifBlank { m.displayName },
            photoUrl = m.photoUrl.takeIf { it.isNotBlank() },
            weeklySteps = c.stepCount,
        )
    }.sortedByDescending { it.weeklySteps }
    val byId = members.associateBy { it.userId }
    return WeeklyCardUiState(
        isLoading = false,
        group = group.copy(weeklyTarget = week.targetSteps.toInt()),
        weekStart = runCatching { LocalDate.parse(week.weekStart) }.getOrNull(),
        members = members,
        total = week.totalSteps,
        reached = week.goalHit,
        // The streak as that week left it: the group's own, when it is the last one.
        streak = group.streakWeeks.takeIf { group.streakThroughWeek == week.weekStart } ?: 0,
        walks = WeeklyAwards.walks(candidates, elapsed = 7),
        awards = WeeklyAwards.of(candidates, elapsed = 7, weekOver = true)
            .mapNotNull { (id, award) -> byId[id]?.let { it to award } },
        finished = true,
    )
}

/** Pure, so the card's figures can be checked without a screen. */
internal fun weeklyCardOf(
    group: Group,
    memberships: List<Membership>,
    today: LocalDate = WeekWindow.todayFor(group),
): WeeklyCardUiState {
    val weekStart = LocalDate.parse(WeekWindow.startOf(group.startDay, today))
    val week = weekStart.toString()
    val elapsed = (ChronoUnit.DAYS.between(weekStart, today) + 1).toInt().coerceIn(1, 7)

    // Only figures marked as this week's, exactly as Home reads them.
    val candidates = memberships.map { m ->
        AwardCandidate(
            userId = m.userId,
            weeklySteps = m.weeklySteps.takeIf { m.weekStart == week } ?: 0L,
            walkedDays = m.walkedDays.takeIf { m.walkedWeekStart == week },
        )
    }
    val members = memberships.zip(candidates) { m, c ->
        CardMember(
            userId = m.userId,
            name = m.displayName.trim().substringBefore(' ').ifBlank { m.displayName },
            photoUrl = m.photoUrl.takeIf { it.isNotBlank() },
            weeklySteps = c.weeklySteps,
        )
    }.sortedByDescending { it.weeklySteps }
    val total = members.sumOf { it.weeklySteps }
    val reached = group.weeklyTarget > 0 && total >= group.weeklyTarget
    val byId = members.associateBy { it.userId }

    return WeeklyCardUiState(
        isLoading = false,
        group = group,
        weekStart = weekStart,
        members = members,
        total = total,
        reached = reached,
        streak = group.streakFor(week) + if (reached) 1 else 0,
        walks = WeeklyAwards.walks(candidates, elapsed),
        awards = WeeklyAwards.of(candidates, elapsed).mapNotNull { (id, award) -> byId[id]?.let { it to award } },
    )
}
