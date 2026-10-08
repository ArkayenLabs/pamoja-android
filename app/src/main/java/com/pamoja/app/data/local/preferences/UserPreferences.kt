package com.pamoja.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pamoja.app.domain.model.ThemePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Internal rather than private so [com.pamoja.app.data.local.activity.ActivityLogStore]
 * can share it. Creating a second `preferencesDataStore` with the same file
 * name throws at runtime, so there must be exactly one declaration per file and
 * every store in the module has to reach for this one.
 */
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pamoja_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val KEY_USER_ID                  = stringPreferencesKey("user_id")
        val KEY_USER_NAME                = stringPreferencesKey("user_name")
        val KEY_INTRO_SEEN               = booleanPreferencesKey("intro_seen")
        val KEY_IS_ONBOARDED             = booleanPreferencesKey("is_onboarded")
        val KEY_HEALTH_CONNECT_GRANTED   = booleanPreferencesKey("health_connect_granted")
        val KEY_ACTIVE_GROUP_ID          = stringPreferencesKey("active_group_id")
        val KEY_STEP_BASELINE_DATE       = stringPreferencesKey("step_baseline_date")
        val KEY_STEP_BASELINE_VALUE      = stringPreferencesKey("step_baseline_value")

        // Whether the notification primer has been shown. Not whether the
        // permission was granted, which the OS already knows: this only stops
        // us asking a second time after someone said "not now".
        val KEY_NOTIFICATION_PRIMER_SHOWN = booleanPreferencesKey("notification_primer_shown")

        // Once per installation, like the intro: survives clearAll().
        val KEY_INSTALL_REFERRER_CHECKED = booleanPreferencesKey("install_referrer_checked")

        // Home's first-run coach marks were shown (finished or skipped). Per
        // installation, like the intro: survives clearAll().
        val KEY_HOME_TOUR_SEEN = booleanPreferencesKey("home_tour_seen")

        // Groups whose ended Premium preview this account was already told about.
        val KEY_PREVIEW_END_SEEN = stringSetPreferencesKey("preview_end_seen_groups")

        // Consecutive notifications sent without the user opening one.
        // Drives engagement backoff: if we are noise to someone, continuing to
        // send guarantees a mute or an uninstall.

        // Last observed leaderboard position + group size. Comparing across
        // syncs is how we detect locally that someone overtook you, without
        // needing a server. Size is stored too: if the group changed size, a
        // rank shift may just be someone joining or leaving, not a real pass.

        // An invite code from a link or QR that we could not act on yet,
        // usually because the user had not finished onboarding. Held here so
        // the invite survives signup, then Home offers a clear resume-or-dismiss
        // choice instead of silently losing it or forcing it into a later session.
        val KEY_PENDING_INVITE_CODE      = stringPreferencesKey("pending_invite_code")
        val KEY_PENDING_INVITE_SAVED_AT  = longPreferencesKey("pending_invite_saved_at")

        // A property of this phone, not of the account, which is why it is the
        // one key that survives clearAll(). See the note there.
        val KEY_THEME                    = stringPreferencesKey("theme_preference")

        // When steps last actually reached Firestore. Written only on a
        // successful sync, so "last synced" never claims a run that failed.
        val KEY_LAST_SYNC_TIME           = longPreferencesKey("last_sync_time")

        // Minutes from midnight, so a time survives locale and timezone changes
        // that a formatted string would not.
        val KEY_QUIET_START_MINUTE       = intPreferencesKey("quiet_hours_start_minute")
        val KEY_QUIET_END_MINUTE         = intPreferencesKey("quiet_hours_end_minute")

        // Bounded, account-scoped receipt keys for FCM's at-least-once delivery.
        // Kept local: the backend does not need to know which tray entries this
        // installation has already rendered.
        val KEY_REMOTE_NOTIFICATION_EVENTS = stringSetPreferencesKey("remote_notification_events")

        // One receipt per account, group and week. A completed goal deserves a
        // real in-app moment, but replaying it on every visit would turn the
        // celebration into an interruption.
        val KEY_WEEKLY_GOAL_CELEBRATIONS = stringSetPreferencesKey("weekly_goal_celebrations")

        private const val MAX_REMOTE_NOTIFICATION_EVENTS = 50
        private const val MAX_WEEKLY_GOAL_CELEBRATIONS = 100
    }

    val userId: Flow<String?>  = context.dataStore.data.map { it[KEY_USER_ID] }
    val userName: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }
    val hasSeenIntro: Flow<Boolean> = context.dataStore.data.map { it[KEY_INTRO_SEEN] ?: false }
    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_ONBOARDED] ?: false }
    val isHealthConnectGranted: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_HEALTH_CONNECT_GRANTED] ?: false
    }
    val activeGroupId: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_GROUP_ID] }
    val stepBaselineDate: Flow<String?>  = context.dataStore.data.map { it[KEY_STEP_BASELINE_DATE] }
    val stepBaselineValue: Flow<Long>    = context.dataStore.data.map {
        it[KEY_STEP_BASELINE_VALUE]?.toLongOrNull() ?: 0L
    }
    val themePreference: Flow<ThemePreference> = context.dataStore.data.map {
        ThemePreference.fromName(it[KEY_THEME])
    }

    suspend fun saveThemePreference(preference: ThemePreference) {
        context.dataStore.edit { it[KEY_THEME] = preference.name }
    }

    /** 0 when steps have never successfully synced on this install. */
    val lastSyncTime: Flow<Long> = context.dataStore.data.map {
        it[KEY_LAST_SYNC_TIME] ?: 0L
    }

    suspend fun saveLastSyncTime(timestamp: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC_TIME] = timestamp }
    }

    /**
     * Whether we have already explained why notifications are worth allowing.
     *
     * Tracked separately from the OS permission because the two mean different
     * things. Android 13+ gives one real chance at the system dialog: a denial
     * makes every later request return denied without showing anything. So the
     * primer is asked first, and a "not now" leaves that one chance unspent.
     */
    val notificationPrimerShown: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_NOTIFICATION_PRIMER_SHOWN] ?: false
    }

    suspend fun setNotificationPrimerShown() {
        context.dataStore.edit { it[KEY_NOTIFICATION_PRIMER_SHOWN] = true }
    }

    /**
     * Whether Play's install referrer has been read for this installation.
     * Read once: signing out and in again must not bring back the invite a
     * person installed from weeks ago.
     */
    val installReferrerChecked: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_INSTALL_REFERRER_CHECKED] ?: false
    }

    suspend fun setInstallReferrerChecked() {
        context.dataStore.edit { it[KEY_INSTALL_REFERRER_CHECKED] = true }
    }

    /** Whether Home's coach marks have run on this installation. */
    val homeTourSeen: Flow<Boolean> = context.dataStore.data.map { it[KEY_HOME_TOUR_SEEN] ?: false }

    suspend fun setHomeTourSeen() {
        context.dataStore.edit { it[KEY_HOME_TOUR_SEEN] = true }
    }

    /** Groups whose ended preview has been offered once; never offered twice. */
    val previewEndSeenGroups: Flow<Set<String>> = context.dataStore.data.map {
        it[KEY_PREVIEW_END_SEEN].orEmpty()
    }

    suspend fun markPreviewEndSeen(groupId: String) {
        context.dataStore.edit { it[KEY_PREVIEW_END_SEEN] = it[KEY_PREVIEW_END_SEEN].orEmpty() + groupId }
    }

    // Muted notification categories used to be stored here. They are read from
    // the Android notification channels instead, via SmartNotificationHelper,
    // because the OS exposes those same four channels in system settings and
    // two independent switches for one thing could disagree with each other.

    /** Defaults to the 22:00 to 08:00 window the engine used to hardcode. */
    val quietHoursStartMinute: Flow<Int> = context.dataStore.data.map {
        it[KEY_QUIET_START_MINUTE] ?: (22 * 60)
    }
    val quietHoursEndMinute: Flow<Int> = context.dataStore.data.map {
        it[KEY_QUIET_END_MINUTE] ?: (8 * 60)
    }

    suspend fun saveQuietHours(startMinute: Int, endMinute: Int) {
        context.dataStore.edit {
            it[KEY_QUIET_START_MINUTE] = startMinute
            it[KEY_QUIET_END_MINUTE] = endMinute
        }
    }

    /** Returns true exactly once for a remote event on this installation. */
    suspend fun consumeRemoteNotificationEvent(userId: String, eventId: String): Boolean {
        val receipt = "$userId:$eventId"
        var isNew = false
        context.dataStore.edit { preferences ->
            val existing = preferences[KEY_REMOTE_NOTIFICATION_EVENTS].orEmpty()
            if (receipt !in existing) {
                isNew = true
                preferences[KEY_REMOTE_NOTIFICATION_EVENTS] =
                    (existing + receipt).toList()
                        .takeLast(MAX_REMOTE_NOTIFICATION_EVENTS)
                        .toSet()
            }
        }
        return isNew
    }

    private val catchUpKey = stringPreferencesKey("step_catch_up_day")
    suspend fun hasCaughtUpSteps(userId: String, today: String): Boolean =
        context.dataStore.data.first()[catchUpKey] == "$userId:$today"

    suspend fun markStepsCaughtUp(userId: String, today: String) {
        context.dataStore.edit { it[catchUpKey] = "$userId:$today" }
    }

    /** Returns true once per account, group and completed week on this install. */
    suspend fun consumeWeeklyGoalCelebration(
        userId: String,
        groupId: String,
        weekStart: String,
    ): Boolean {
        val receipt = "$userId:$groupId:$weekStart"
        var isNew = false
        context.dataStore.edit { preferences ->
            val existing = preferences[KEY_WEEKLY_GOAL_CELEBRATIONS].orEmpty()
            if (receipt !in existing) {
                isNew = true
                preferences[KEY_WEEKLY_GOAL_CELEBRATIONS] =
                    (existing + receipt).toList()
                        .takeLast(MAX_WEEKLY_GOAL_CELEBRATIONS)
                        .toSet()
            }
        }
        return isNew
    }

    data class PendingInvite(
        val code: String,
        val savedAt: Long,
    )

    val pendingInvite: Flow<PendingInvite?> = context.dataStore.data.map { preferences ->
        val code = preferences[KEY_PENDING_INVITE_CODE]
        if (code.isNullOrBlank()) null else PendingInvite(
            code = code,
            // Zero deliberately expires values written by an older APK that
            // had no timestamp. Resurfacing an unknowably old invitation is
            // exactly the surprise this record now prevents.
            savedAt = preferences[KEY_PENDING_INVITE_SAVED_AT] ?: 0L,
        )
    }

    suspend fun savePendingInviteCode(
        code: String,
        savedAt: Long = System.currentTimeMillis(),
    ) {
        context.dataStore.edit {
            it[KEY_PENDING_INVITE_CODE] = code
            it[KEY_PENDING_INVITE_SAVED_AT] = savedAt
        }
    }

    suspend fun clearPendingInviteCode() {
        context.dataStore.edit {
            it.remove(KEY_PENDING_INVITE_CODE)
            it.remove(KEY_PENDING_INVITE_SAVED_AT)
        }
    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { it[KEY_USER_ID] = userId }
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[KEY_USER_NAME] = name }
    }

    suspend fun setIntroSeen(seen: Boolean) {
        context.dataStore.edit { it[KEY_INTRO_SEEN] = seen }
    }

    suspend fun setOnboarded(completed: Boolean) {
        context.dataStore.edit { it[KEY_IS_ONBOARDED] = completed }
    }

    suspend fun setHealthConnectGranted(granted: Boolean) {
        context.dataStore.edit { it[KEY_HEALTH_CONNECT_GRANTED] = granted }
    }

    suspend fun saveActiveGroupId(groupId: String) {
        context.dataStore.edit { it[KEY_ACTIVE_GROUP_ID] = groupId }
    }

    suspend fun saveStepBaseline(date: String, hardwareCount: Long) {
        context.dataStore.edit {
            it[KEY_STEP_BASELINE_DATE]  = date
            it[KEY_STEP_BASELINE_VALUE] = hardwareCount.toString()
        }
    }

    /**
     * Wipes everything account-scoped, on sign out and on account deletion.
     *
     * Theme, the completed product introduction, Home's coach marks and the
     * install referrer check are deliberately carried across. They describe this installation,
     * not the signed-in account. Logging out must not replay first-run education.
     */
    suspend fun clearAll() {
        val theme = context.dataStore.data.map { it[KEY_THEME] }.first()
        val introSeen = context.dataStore.data.map { it[KEY_INTRO_SEEN] }.first()
        val referrerChecked = context.dataStore.data.map { it[KEY_INSTALL_REFERRER_CHECKED] }.first()
        val tourSeen = context.dataStore.data.map { it[KEY_HOME_TOUR_SEEN] }.first()
        context.dataStore.edit { prefs ->
            prefs.clear()
            theme?.let { prefs[KEY_THEME] = it }
            introSeen?.let { prefs[KEY_INTRO_SEEN] = it }
            referrerChecked?.let { prefs[KEY_INSTALL_REFERRER_CHECKED] = it }
            tourSeen?.let { prefs[KEY_HOME_TOUR_SEEN] = it }
        }
    }

}
