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
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetMyStepsForWeekUseCase
import com.pamoja.app.domain.usecase.GetUserGroupsUseCase
import com.pamoja.app.domain.usecase.GetUserUseCase
import com.pamoja.app.util.WorkManagerScheduler
import com.pamoja.app.widgets.PamojaWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import javax.inject.Inject

data class PersonalDay(
    val date: LocalDate,
    val steps: Long,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val groups: List<Group> = emptyList(),
    val userName: String = "",
    val userPhotoUrl: String? = null,
    /** The seven calendar days in Pamoja's stable Monday-to-Sunday personal week. */
    val personalDays: List<PersonalDay> = emptyList(),
    val hasLoadedPersonalSteps: Boolean = false,
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
     * Checked here as a recovery path after the contextual group connection.
     * Anyone who declined it, reinstalled the app, or revoked it in system
     * settings still needs a clear explanation of why steps are not arriving.
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
    val hasContent: Boolean get() = groups.isNotEmpty() || hasLoadedPersonalSteps

    val showEmptyState: Boolean get() = hasLoadedOnce && groups.isEmpty() && error == null

    /** A full-screen error is only right when there is nothing else to show. */
    val showErrorState: Boolean get() = error != null && !hasContent

    /** Otherwise the error is a passing note over content that still stands. */
    val showErrorSnackbar: Boolean get() = error != null && hasContent

    val personalWeekSteps: Long get() = personalDays.sumOf(PersonalDay::steps)

    val todaySteps: Long get() = personalDays
        .firstOrNull { it.date == LocalDate.now() }
        ?.steps
        ?: 0L
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val getMyStepsForWeekUseCase: GetMyStepsForWeekUseCase,
    private val userPreferences: UserPreferences,
    private val analyticsManager: AnalyticsManager,
    private val connectivityObserver: ConnectivityObserver,
    private val activityLog: ActivityLogStore,
    private val healthConnectReader: HealthConnectReader,
    private val workManagerScheduler: WorkManagerScheduler,
    private val widgetUpdater: PamojaWidgetUpdater,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var homeScreenReachedLogged = false

    /**
     * Exactly one Home data collector may exist at a time.
     *
     * Pull-to-refresh and Retry both restart the stream. Without keeping its
     * Job, every call launched another permanent Firestore listener inside
     * viewModelScope, so the screen became progressively busier the more it was
     * refreshed.
     */
    private var homeLoadJob: Job? = null
    private var personalStepsJob: Job? = null
    private var personalStepEntries: List<StepEntry> = emptyList()
    private var deviceTodaySteps: Long? = null

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
        observeUserName()
        loadHome()
    }

    /**
     * Re-read on every resume, since permission can be granted or revoked in
     * system settings while the app is alive.
     */
    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val needed = _uiState.value.groups.isNotEmpty() &&
                !healthPromptDismissed &&
                healthConnectReader.isAvailable() &&
                !healthConnectReader.hasPermission()
            _uiState.value = _uiState.value.copy(needsHealthConnect = needed)
            refreshDeviceTodaySteps()
        }
    }

    fun dismissHealthConnectPrompt() {
        healthPromptDismissed = true
        _uiState.value = _uiState.value.copy(needsHealthConnect = false)
    }

    /** Records the dashboard request before the system-owned dialog opens. */
    fun onHealthConnectPermissionRequested() {
        analyticsManager.logHealthConnectPermissionRequested()
    }

    /**
     * Applies the platform result without routing through onboarding again.
     *
     * A grant immediately removes the warning and starts the normal sync path.
     * A denial leaves the explanation visible so zero steps never look like a
     * mysterious data bug.
     */
    fun onHealthConnectPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            userPreferences.setHealthConnectGranted(granted)
            _uiState.value = _uiState.value.copy(needsHealthConnect = !granted)
            if (granted) {
                analyticsManager.logHealthConnectPermissionGranted()
                workManagerScheduler.syncSoon()
                refreshDeviceTodaySteps()
            } else {
                analyticsManager.logHealthConnectPermissionDenied()
            }
        }
    }

    /**
     * Keeps the greeting tied to the stored name for as long as this ViewModel
     * lives.
     *
     * Collected rather than read once, because HomeViewModel is not recreated
     * when you go to Edit Profile and come back: the ViewModel survives, so a
     * one-shot read taken in [loadHome] left the greeting on the old name until
     * a pull to refresh or a process death. Editing your name and returning to
     * a dashboard still greeting the previous one is the bug this fixes.
     *
     * EditProfileViewModel.save writes the new name here as well as to
     * Firestore, so this fires the moment a save succeeds.
     */
    private fun observeUserName() {
        viewModelScope.launch {
            userPreferences.userName.collect { name ->
                if (!name.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(userName = name)
                }
            }
        }
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
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            val user = getCurrentUserUseCase()
            if (user == null) {
                widgetUpdater.clear()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = AppError.SessionExpired(),
                )
                return@launch
            }

            // Firestore remains the source of truth for the name visible inside
            // Pamoja groups, even when an auth provider also supplies one.
            // Cache first so the greeting is right on the first frame, then
            // confirm against the profile document.
            _uiState.value = _uiState.value.copy(isLoading = false)

            // Confirms the cached name against the document and writes any
            // difference back. Does not set userName directly: observeUserName
            // is the single writer of that field, so this update reaches the
            // greeting the same way an edit from the profile screen does.
            launch {
                val cachedName = userPreferences.userName.firstOrNull().orEmpty()
                getUserUseCase(user.userId).onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(userPhotoUrl = profile.photoUrl)
                    if (profile.name.isNotBlank() && profile.name != cachedName) {
                        userPreferences.saveUserName(profile.name)
                    }
                }
            }

            observePersonalSteps(user.userId)
            launch { refreshDeviceTodaySteps() }

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
                    val needsHealthConnect = groups.isNotEmpty() &&
                        !healthPromptDismissed &&
                        healthConnectReader.isAvailable() &&
                        !healthConnectReader.hasPermission()
                    _uiState.value = _uiState.value.copy(
                        groups = groups,
                        hasLoadedOnce = true,
                        // A successful emission clears whatever failed before it.
                        error = null,
                        // Only once there is a group to notify about, and only
                        // if we have never explained it before.
                        showNotificationPrimer = groups.isNotEmpty() &&
                            !userPreferences.notificationPrimerShown.first(),
                        needsHealthConnect = needsHealthConnect,
                    )
                    publishWidgets()
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
            refreshDeviceTodaySteps()
            loadHome()
            delay(REFRESH_SPINNER_MIN_MS)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Personal progress is intentionally independent of any one group. A user
     * may belong to circles with different week starts; their own dashboard
     * stays Monday-to-Sunday while every group continues to use its stored
     * shared boundary. A stable boundary also prevents the same account from
     * changing its personal history when the phone locale changes.
     */
    private fun observePersonalSteps(userId: String) {
        personalStepsJob?.cancel()
        personalStepsJob = viewModelScope.launch {
            val startDay = PERSONAL_WEEK_START_DAY
            getMyStepsForWeekUseCase(userId, startDay)
                .catch {
                    _uiState.value = _uiState.value.copy(hasLoadedPersonalSteps = true)
                }
                .collect { entries ->
                    personalStepEntries = entries
                    publishPersonalDays(startDay)
                }
        }
    }

    /** Health Connect is the freshest source for today; Firestore fills history. */
    private suspend fun refreshDeviceTodaySteps() {
        val today = healthConnectReader.readTodaySteps() ?: return
        deviceTodaySteps = today
        publishPersonalDays(PERSONAL_WEEK_START_DAY)
    }

    private fun publishPersonalDays(startDay: java.time.DayOfWeek) {
        val start = LocalDate.parse(WeekWindow.startOf(startDay))
        val today = LocalDate.now()
        val stepsByDate = personalStepEntries.mapNotNull { entry ->
            runCatching { LocalDate.parse(entry.date) to entry.stepCount }.getOrNull()
        }.toMap()
        val days = (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            PersonalDay(
                date = date,
                steps = if (date == today && deviceTodaySteps != null) {
                    deviceTodaySteps!!
                } else {
                    stepsByDate[date] ?: 0L
                },
            )
        }
        _uiState.value = _uiState.value.copy(
            personalDays = days,
            hasLoadedPersonalSteps = true,
        )
        publishWidgets()
    }

    private fun publishWidgets() {
        val state = _uiState.value
        widgetUpdater.publish(
            todaySteps = state.todaySteps,
            weekSteps = state.personalWeekSteps,
            groups = state.groups,
        )
    }

    private companion object {
        /** Long enough that the gesture is acknowledged, short enough not to stall. */
        const val REFRESH_SPINNER_MIN_MS = 450L
        val PERSONAL_WEEK_START_DAY: DayOfWeek = DayOfWeek.MONDAY
    }
}
