package com.pamoja.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.util.NotificationCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationSettingsUiState(
    /**
     * Whether the OS will deliver anything at all.
     *
     * Android 13+ needs POST_NOTIFICATIONS. Without it every toggle below is
     * decoration, so the screen says so rather than letting someone carefully
     * configure four channels that can never fire.
     */
    val systemPermissionGranted: Boolean = true,
    val mutedCategories: Set<NotificationCategory> = emptySet(),
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 8 * 60,
) {
    fun isEnabled(category: NotificationCategory): Boolean = category !in mutedCategories

    /** True when every category is off, which is worth saying out loud. */
    val allMuted: Boolean
        get() = NotificationCategory.entries.all { it in mutedCategories }
}

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userPreferences.mutedNotificationCategories,
                userPreferences.quietHoursStartMinute,
                userPreferences.quietHoursEndMinute,
            ) { muted, start, end ->
                Triple(muted, start, end)
            }.collect { (muted, start, end) ->
                _uiState.value = _uiState.value.copy(
                    // Unknown stored names are dropped rather than crashing, so
                    // renaming a category in a later release cannot break this.
                    mutedCategories = muted.mapNotNull { name ->
                        NotificationCategory.entries.firstOrNull { it.name == name }
                    }.toSet(),
                    quietStartMinute = start,
                    quietEndMinute = end,
                )
            }
        }
    }

    /** Re-read on resume, since the user can revoke it in system settings. */
    fun onSystemPermissionChanged(granted: Boolean) {
        _uiState.value = _uiState.value.copy(systemPermissionGranted = granted)
    }

    fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setCategoryMuted(category.name, muted = !enabled)
        }
    }

    fun setQuietHours(startMinute: Int, endMinute: Int) {
        viewModelScope.launch {
            userPreferences.saveQuietHours(startMinute, endMinute)
        }
    }
}
