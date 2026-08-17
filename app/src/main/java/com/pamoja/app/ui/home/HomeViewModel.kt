package com.pamoja.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.connectivity.ConnectivityObserver
import com.pamoja.app.data.local.activity.ActivityLogStore
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.analytics.AnalyticsManager
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetUserGroupsUseCase
import com.pamoja.app.domain.usecase.GetUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val groups: List<Group> = emptyList(),
    val userName: String = "",
    /**
     * Typed rather than a String so the screen can decide between a full error
     * state and a snackbar, and whether Retry is worth offering.
     */
    val error: AppError? = null,
    val isOffline: Boolean = false,
    /**
     * True once groups have arrived at least once. Distinguishes "still
     * loading" from "genuinely has no groups", which otherwise both look like
     * an empty list and produce an empty state that flashes before content.
     */
    val hasLoadedOnce: Boolean = false,
    /**
     * Whether to explain notifications before the system dialog is spent.
     *
     * Held off until the user actually has a group, since every example the
     * primer gives is about a group and asking someone with none is asking
     * them to imagine why they would care.
     */
    val showNotificationPrimer: Boolean = false,
    /**
     * Drives the pull-to-refresh spinner only.
     *
     * Separate from [isLoading], which swaps in a skeleton. A pull already has
     * content on screen and replacing it with a skeleton would throw away what
     * the user is looking at to show them less.
     */
    val isRefreshing: Boolean = false,
    /** Drives the dot on the activity bell. Zero hides it. */
    val unreadActivityCount: Int = 0,
    /**
     * Health Connect is installed but not permitted, so no steps are arriving.
     *
     * Checked here rather than only during onboarding. The permission screen is
     * reachable from ProfileSetup alone, so anyone who declined it, reinstalled
     * the app, or revoked it in system settings had a permanently stepless app
     * and nothing on any screen saying why.
     *
     * False when Health Connect is unavailable entirely. That is not something
     * the user can act on from here, and a prompt that leads nowhere is worse
     * than silence.
     */
    val needsHealthConnect: Boolean = false,
    /** When these totals last reached the phone. Zero means never. */
    val lastSyncedAt: Long = 0L,
) {
    /** Content is worth showing even mid-error if we already have some. */
    val hasContent: Boolean get() = groups.isNotEmpty()

    val showEmptyState: Boolean get() = hasLoadedOnce && groups.isEmpty() && error == null

    /** A full-screen error is only right when there is nothing else to show. */
    val showErrorState: Boolean get() = error != null && !hasContent

    /** Otherwise the error is a passing note over content that still stands. */
    val showErrorSnackbar: Boolean get() = error != null && hasContent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val userPreferences: UserPreferences,
    private val analyticsManager: AnalyticsManager,
    private val connectivityObserver: ConnectivityObserver,
    private val activityLog: ActivityLogStore,
    private val healthConnectReader: HealthConnectReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var homeScreenReachedLogged = false

    /**
     * Session-scoped, deliberately not persisted.
     *
     * Dismissing means "not now", not "never". Steps genuinely are not syncing,
     * so the prompt should come back next launch; persisting the dismissal
     * would let someone silence it once and never learn why their step count
     * stayed at zero.
     */
    private var healthPromptDismissed = false

    init {
        observeConnectivity()
        observeUnreadActivity()
        loadHome()
    }

    /**
     * Re-read on every resume, since permission can be granted or revoked in
     * system settings while the app is alive.
     */
    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val needed = !healthPromptDismissed &&
                healthConnectReader.isAvailable() &&
                !healthConnectReader.hasPermission()
            _uiState.value = _uiState.value.copy(needsHealthConnect = needed)
        }
    }

    fun dismissHealthConnectPrompt() {
        healthPromptDismissed = true
        _uiState.value = _uiState.value.copy(needsHealthConnect = false)
    }

    private fun observeUnreadActivity() {
        viewModelScope.launch {
            activityLog.unreadCount.collect { count ->
                _uiState.value = _uiState.value.copy(unreadActivityCount = count)
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
        // Same source the group screen uses, so the two cannot claim different
        // times for the same figures.
        viewModelScope.launch {
            userPreferences.lastSyncTime.collect { at ->
                _uiState.value = _uiState.value.copy(lastSyncedAt = at)
            }
        }
    }

    private fun loadHome() {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            if (user == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = AppError.SessionExpired(),
                )
                return@launch
            }

            // The auth record carries a UID and nothing else, so user.name here
            // is always blank and the greeting fell back to "there" for
            // everyone, on every sign-in method. The display name lives on the
            // Firestore profile. Cache first so the greeting is right on the
            // first frame, then confirm against the document.
            val cachedName = userPreferences.userName.firstOrNull().orEmpty()
            _uiState.value = _uiState.value.copy(userName = cachedName, isLoading = false)

            getUserUseCase(user.userId).onSuccess { profile ->
                if (profile.name.isNotBlank()) {
                    if (profile.name != cachedName) userPreferences.saveUserName(profile.name)
                    _uiState.value = _uiState.value.copy(userName = profile.name)
                }
            }

            if (!homeScreenReachedLogged) {
                homeScreenReachedLogged = true
                analyticsManager.logHomeScreenReached()
            }

            getUserGroupsUseCase(user.userId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        error = e.toAppError(),
                    )
                }
                .collect { groups ->
                    _uiState.value = _uiState.value.copy(
                        groups = groups,
                        hasLoadedOnce = true,
                        // A successful emission clears whatever failed before it.
                        error = null,
                        // Only once there is a group to notify about, and only
                        // if we have never explained it before.
                        showNotificationPrimer = groups.isNotEmpty() &&
                            !userPreferences.notificationPrimerShown.first(),
                    )
                }
        }
    }

    /**
     * Marks the primer answered, whichever way it was answered.
     *
     * "Not now" is recorded exactly like "turn on", because the point is to ask
     * once. Re-prompting someone who declined is how an app earns a permanent
     * denial, and on Android 13+ that cannot be undone from inside the app.
     */
    fun onNotificationPrimerAnswered() {
        viewModelScope.launch {
            userPreferences.setNotificationPrimerShown()
            _uiState.value = _uiState.value.copy(showNotificationPrimer = false)
        }
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        loadHome()
    }

    /**
     * Pull to refresh.
     *
     * Groups already arrive over a Firestore snapshot listener, so this rarely
     * produces different data. It is still worth having: it is the gesture
     * people reach for when something looks stale, and it is the way back from
     * a failed load without hunting for a Retry button. The spinner is held
     * briefly even when the answer returns instantly, because a refresh that
     * flickers and vanishes reads as one that did not run.
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            loadHome()
            delay(REFRESH_SPINNER_MIN_MS)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        /** Long enough that the gesture is acknowledged, short enough not to stall. */
        const val REFRESH_SPINNER_MIN_MS = 450L
    }
}
