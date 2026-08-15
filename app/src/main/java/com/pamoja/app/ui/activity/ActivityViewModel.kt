package com.pamoja.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.activity.ActivityLogStore
import com.pamoja.app.domain.model.ActivityItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityUiState(
    val isLoading: Boolean = true,
    val items: List<ActivityItem> = emptyList(),
) {
    /**
     * Distinguishes "still reading the log" from "nothing has ever arrived",
     * which otherwise both look like an empty list and flash an empty state
     * before content lands.
     */
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/**
 * What Pamoja has said to this person, after the tray forgot it.
 */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val activityLog: ActivityLogStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            activityLog.items.collect { items ->
                _uiState.value = ActivityUiState(isLoading = false, items = items)
            }
        }
    }

    /**
     * Marks everything read on opening.
     *
     * The badge answers "is there anything I have not seen". Once the list is
     * open the answer is no, so clearing it here rather than per row avoids a
     * badge that survives having read past the thing it counted.
     */
    fun onOpened() {
        viewModelScope.launch { activityLog.markAllRead() }
    }

    fun clearAll() {
        viewModelScope.launch { activityLog.clear() }
    }
}
