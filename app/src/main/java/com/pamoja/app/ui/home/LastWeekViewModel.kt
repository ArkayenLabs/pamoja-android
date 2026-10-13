package com.pamoja.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.usecase.ObserveLastFinishedWeekUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The shown group's last finished week, for Home's "Last week" row. Kept out
 * of GroupViewModel, whose member flow is a critical area with its own tests:
 * this is one free read of `weeks/{streakThroughWeek}`, the same the weekly
 * review makes, and nothing on Home depends on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LastWeekViewModel @Inject constructor(
    observeLastFinishedWeek: ObserveLastFinishedWeekUseCase,
) : ViewModel() {

    private val group = MutableStateFlow<Group?>(null)

    val lastWeek: StateFlow<GroupWeekSummary?> = group
        .distinctUntilChanged { a, b -> a?.groupId == b?.groupId && a?.streakThroughWeek == b?.streakThroughWeek }
        .flatMapLatest { shown ->
            // A row that fails to load is simply not shown; Home has enough
            // to say about the current week without an error about the last.
            if (shown == null) flowOf(null) else observeLastFinishedWeek(shown).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun watch(shown: Group?) {
        group.value = shown
    }
}
