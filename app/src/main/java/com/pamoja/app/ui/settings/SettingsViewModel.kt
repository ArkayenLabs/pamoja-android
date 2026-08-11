package com.pamoja.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.data.remote.auth.GoogleCredentialClient
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.usecase.DeleteAccountUseCase
import com.pamoja.app.domain.usecase.GetAuthMethodsUseCase
import com.pamoja.app.domain.usecase.GetCurrentUserUseCase
import com.pamoja.app.domain.usecase.GetUserUseCase
import com.pamoja.app.domain.usecase.ReauthenticateWithEmailUseCase
import com.pamoja.app.domain.usecase.ReauthenticateWithGoogleUseCase
import com.pamoja.app.domain.usecase.ReauthenticateWithPhoneUseCase
import com.pamoja.app.domain.usecase.SignOutUseCase
import com.pamoja.app.domain.usecase.StartPhoneVerificationUseCase
import com.pamoja.app.util.NotificationContext
import com.pamoja.app.util.SmartNotificationEngine
import com.pamoja.app.util.SmartNotificationHelper
import com.pamoja.app.util.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the user must prove it is them before the account can be deleted. */
enum class ReauthMethod { Google, Password, Phone }

/**
 * Whether steps can reach us, and if not, why not.
 *
 * The three cases need different words and different actions, which is the
 * whole reason this is not a Boolean: [Unavailable] cannot be fixed by granting
 * anything, and [NotConnected] cannot be fixed by installing anything.
 */
enum class HealthStatus {
    /** Permission granted, steps are flowing. */
    Connected,

    /** Health Connect is present but the read permission is not granted. */
    NotConnected,

    /** No Health Connect on this device at all, so nothing to grant. */
    Unavailable,
}

data class SettingsUiState(
    val isLoading: Boolean = false,

    /** A failure that came back from a repository. The screen renders its copy. */
    val error: AppError? = null,

    /**
     * A message this screen raised itself, as a string resource id.
     *
     * An Int rather than text, so the ViewModel never holds user-facing copy and
     * never needs a Context to look one up. [messageArg] fills a format
     * placeholder where the message has one.
     */
    val messageRes: Int? = null,
    val messageArg: Int? = null,
    val userName: String = "",
    val userId: String = "",
    /**
     * Shown as a one-line summary in the profile header.
     *
     * Collected during onboarding and, until the profile editor existed,
     * displayed nowhere at all.
     */
    val age: Int? = null,
    val height: Float? = null,
    val weight: Float? = null,
    val theme: ThemePreference = ThemePreference.System,
    val healthStatus: HealthStatus = HealthStatus.Connected,
    /** Epoch millis of the last successful sync, 0 when it has never happened. */
    val lastSyncTime: Long = 0L,
    val isSyncing: Boolean = false,
    val isSignedOut: Boolean = false,
    /** Non-null when deletion is waiting on the user re-confirming who they are. */
    val reauthRequired: ReauthMethod? = null,
    /** The number the re-verification code went to, shown so a wrong one is spotted. */
    val reauthPhoneNumber: String? = null,
    /** Set once the SMS is away, which is what swaps the dialog to code entry. */
    val reauthVerificationId: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val userPreferences: UserPreferences,
    private val notificationHelper: SmartNotificationHelper,
    private val getAuthMethodsUseCase: GetAuthMethodsUseCase,
    private val reauthenticateWithGoogleUseCase: ReauthenticateWithGoogleUseCase,
    private val reauthenticateWithEmailUseCase: ReauthenticateWithEmailUseCase,
    private val reauthenticateWithPhoneUseCase: ReauthenticateWithPhoneUseCase,
    private val startPhoneVerificationUseCase: StartPhoneVerificationUseCase,
    private val googleCredentialClient: GoogleCredentialClient,
    private val healthConnectReader: HealthConnectReader,
    private val workManagerScheduler: WorkManagerScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserDetails()
        refreshHealthStatus()
        viewModelScope.launch {
            userPreferences.themePreference.collect { theme ->
                _uiState.value = _uiState.value.copy(theme = theme)
            }
        }
        viewModelScope.launch {
            userPreferences.lastSyncTime.collect { time ->
                _uiState.value = _uiState.value.copy(lastSyncTime = time)
            }
        }
        viewModelScope.launch {
            workManagerScheduler.manualSyncState().collect { infos ->
                _uiState.value = _uiState.value.copy(
                    isSyncing = infos.any { !it.state.isFinished },
                )
            }
        }
    }

    /**
     * Re-checks Health Connect. Called on entering Settings, not just at
     * construction, because permission can be revoked in system settings while
     * the app is alive and the app would otherwise keep claiming Connected.
     */
    fun refreshHealthStatus() {
        viewModelScope.launch {
            val status = when {
                !healthConnectReader.isAvailable() -> HealthStatus.Unavailable
                healthConnectReader.hasPermission() -> HealthStatus.Connected
                else -> HealthStatus.NotConnected
            }
            _uiState.value = _uiState.value.copy(healthStatus = status)
        }
    }

    fun syncNow() {
        workManagerScheduler.syncNow()
    }

    /**
     * Persists the choice only. The theme itself is driven from MainActivity,
     * which collects the same flow, so the app recolours from the write rather
     * than from anything this screen does.
     */
    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch { userPreferences.saveThemePreference(preference) }
    }

    /** Re-reads the profile, for coming back from the editor with it changed. */
    fun refresh() {
        loadUserDetails()
    }

    private fun loadUserDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Get local cached values first
            val localUserId = userPreferences.userId.firstOrNull() ?: ""
            val localName = userPreferences.userName.firstOrNull() ?: ""
            
            _uiState.value = _uiState.value.copy(
                userId = localUserId,
                userName = localName
            )

            // Verify with remote source if available
            val currentUser = getCurrentUserUseCase()
            if (currentUser != null) {
                val remoteUserResult = getUserUseCase(currentUser.userId)
                remoteUserResult.fold(
                    onSuccess = { user ->
                        // Cache remote name locally if it changed
                        if (user.name != localName) {
                            userPreferences.saveUserName(user.name)
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            userId = user.userId,
                            userName = user.name,
                            age = user.age,
                            height = user.height,
                            weight = user.weight
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = signOutUseCase()
            result.fold(
                onSuccess = {
                    userPreferences.clearAll()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedOut = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.toAppError()
                    )
                }
            )
        }
    }

    fun deleteAccount(justReauthenticated: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = deleteAccountUseCase(justReauthenticated)
            result.fold(
                onSuccess = {
                    userPreferences.clearAll()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedOut = true
                    )
                },
                onFailure = { error ->
                    if (error is AuthRepository.RecentLoginRequired) {
                        // Nothing has been deleted yet. Ask them to confirm, then
                        // this runs again from the top.
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            reauthRequired = reauthMethodForCurrentUser(),
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.toAppError()
                        )
                    }
                }
            )
        }
    }

    /**
     * Google first because it is one tap, then password, then phone, which costs
     * a real SMS. Accounts with several methods get the cheapest one.
     */
    private suspend fun reauthMethodForCurrentUser(): ReauthMethod {
        val methods = getAuthMethodsUseCase()
        return when {
            methods.hasGoogle -> ReauthMethod.Google
            methods.hasEmail -> ReauthMethod.Password
            else -> ReauthMethod.Phone
        }
    }

    /** Re-confirms via Google, then retries the deletion. */
    fun reauthenticateWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val token = googleCredentialClient.getIdToken(activity).getOrElse { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    reauthRequired = null,
                    // A dismissed Google sheet is a choice, not a failure.
                    error = if (e is GoogleCredentialClient.Cancelled) null else e.toAppError(),
                )
                return@launch
            }

            reauthenticateWithGoogleUseCase(token).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(reauthRequired = null)
                    deleteAccount(justReauthenticated = true)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        reauthRequired = null,
                        messageRes = R.string.settings_reauth_failed,
                    )
                }
            )
        }
    }

    /** Re-confirms with the account password, then retries the deletion. */
    fun reauthenticateWithPassword(password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            reauthenticateWithEmailUseCase(password).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(reauthRequired = null)
                    deleteAccount(justReauthenticated = true)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.toAppError(),
                    )
                }
            )
        }
    }

    /**
     * Sends a fresh SMS to the number already on the account.
     *
     * The number is read from Firebase rather than typed, so there is nothing to
     * mistype and no way to aim the message somewhere else.
     */
    fun sendReauthCode(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val phoneNumber = getAuthMethodsUseCase().phoneNumber
            if (phoneNumber.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    reauthRequired = null,
                    messageRes = R.string.settings_no_phone,
                )
                return@launch
            }

            startPhoneVerificationUseCase(phoneNumber, activity).fold(
                onSuccess = { verification ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        reauthPhoneNumber = phoneNumber,
                        reauthVerificationId = verification.verificationId,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messageRes = R.string.settings_code_send_failed,
                    )
                }
            )
        }
    }

    /** Confirms the SMS code, then retries the deletion. */
    fun reauthenticateWithPhone(code: String) {
        val verificationId = _uiState.value.reauthVerificationId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            reauthenticateWithPhoneUseCase(verificationId, code).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        reauthRequired = null,
                        reauthVerificationId = null,
                        reauthPhoneNumber = null,
                    )
                    deleteAccount(justReauthenticated = true)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messageRes = if (e.toAppError() is AppError.Expired) {
                            R.string.settings_code_expired
                        } else {
                            R.string.settings_code_wrong
                        },
                    )
                }
            )
        }
    }

    fun cancelReauth() {
        _uiState.value = _uiState.value.copy(
            reauthRequired = null,
            reauthVerificationId = null,
            reauthPhoneNumber = null,
            isLoading = false,
        )
    }

    /**
     * Posts one notification per channel, ignoring every gate.
     *
     * Only reachable from a debug build. Guarded again at the call site, so
     * neither the row nor this path can ship.
     */
    fun sendDebugNotifications() {
        if (!com.pamoja.app.BuildConfig.DEBUG) return

        viewModelScope.launch {
            val name = _uiState.value.userName.ifBlank { "Ravi" }

            // Values chosen so every candidate builds: a goal just reached, a
            // teammate just ahead, a new member and a streak all at once, which
            // never happens in reality but exercises all four channels.
            val context = NotificationContext(
                userName = name,
                groupId = "debug-group",
                groupName = "Debug Group",
                memberCount = 5,
                groupAgeMs = 14L * 24 * 60 * 60 * 1000,
                weeklyGoal = 70_000,
                groupStepsTotal = 70_500,
                userStepsThisWeek = 14_200,
                userStepsToday = 6_400,
                daysLeftInWeek = 2,
                memberJustAheadName = "Priya",
                stepsToOvertakeMemberAhead = 320,
                memberWhoJustPassedYouName = "Arjun",
                goalReachedJustNow = true,
                newMemberName = "Meera",
                isFirstEverSync = true,
                currentStreakDays = 6,
                streakAtRiskToday = true,
                now = java.time.LocalTime.now(),
                today = java.time.LocalDate.now().dayOfWeek,
                hoursSinceLastNotification = 999,
                consecutiveIgnored = 0,
                rotationSeed = java.time.LocalDate.now().dayOfYear,
            )

            val samples = SmartNotificationEngine.debugSamples(context)
            samples.forEach { notificationHelper.show(it) }

            _uiState.value = _uiState.value.copy(
                messageRes = R.string.settings_debug_posted,
                messageArg = samples.size
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            error = null,
            messageRes = null,
            messageArg = null,
        )
    }
}
