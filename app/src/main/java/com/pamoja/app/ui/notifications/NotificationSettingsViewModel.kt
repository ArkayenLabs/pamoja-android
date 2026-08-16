package com.pamoja.app.ui.notifications

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.util.NotificationCategory
import com.pamoja.app.util.SmartNotificationHelper
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
    private val notificationHelper: SmartNotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userPreferences.quietHoursStartMinute,
                userPreferences.quietHoursEndMinute,
            ) { start, end ->
                start to end
            }.collect { (start, end) ->
                _uiState.value = _uiState.value.copy(
                    quietStartMinute = start,
                    quietEndMinute = end,
                )
            }
        }
        refreshCategories()
    }

    /**
     * Re-read the real channel states.
     *
     * Called on every resume, because the only way to change a category is in
     * system settings, so the app is always returning from the place the change
     * was made.
     */
    fun refreshCategories() {
        _uiState.value = _uiState.value.copy(
            mutedCategories = notificationHelper.mutedCategories(),
        )
    }

    /** Re-read on resume, since the user can revoke it in system settings. */
    fun onSystemPermissionChanged(granted: Boolean) {
        _uiState.value = _uiState.value.copy(systemPermissionGranted = granted)
    }

    /**
     * Where to send the user to change this category.
     *
     * Android owns channel importance once the channel exists and will not let
     * an app write it, so there is nothing to toggle here. The switch reports
     * the truth and this opens the one place it can be changed.
     */
    fun settingsIntentFor(category: NotificationCategory): Intent =
        notificationHelper.channelSettingsIntent(category)

    fun setQuietHours(startMinute: Int, endMinute: Int) {
        viewModelScope.launch {
            userPreferences.saveQuietHours(startMinute, endMinute)
        }
    }
}
