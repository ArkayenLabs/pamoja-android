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
import com.pamoja.app.domain.model.UnitSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pamoja_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val KEY_USER_ID                  = stringPreferencesKey("user_id")
        val KEY_USER_NAME                = stringPreferencesKey("user_name")
        val KEY_IS_ONBOARDED             = booleanPreferencesKey("is_onboarded")
        val KEY_HEALTH_CONNECT_GRANTED   = booleanPreferencesKey("health_connect_granted")
        val KEY_ACTIVE_GROUP_ID          = stringPreferencesKey("active_group_id")
        val KEY_STEP_BASELINE_DATE       = stringPreferencesKey("step_baseline_date")
        val KEY_STEP_BASELINE_VALUE      = stringPreferencesKey("step_baseline_value")
        val KEY_LAST_NOTIFICATION_TIME   = longPreferencesKey("last_notification_time")

        // Group total at the previous sync, lets us fire the "goal reached"
        // notification exactly once, on the crossing, rather than every sync.
        val KEY_LAST_KNOWN_GROUP_TOTAL   = longPreferencesKey("last_known_group_total")

        // Whether the notification primer has been shown. Not whether the
        // permission was granted, which the OS already knows: this only stops
        // us asking a second time after someone said "not now".
        val KEY_NOTIFICATION_PRIMER_SHOWN = booleanPreferencesKey("notification_primer_shown")

        // Consecutive notifications sent without the user opening one.
        // Drives engagement backoff: if we are noise to someone, continuing to
        // send guarantees a mute or an uninstall.
        val KEY_CONSECUTIVE_IGNORED      = intPreferencesKey("consecutive_ignored_notifications")

        // Last observed leaderboard position + group size. Comparing across
        // syncs is how we detect locally that someone overtook you, without
        // needing a server. Size is stored too: if the group changed size, a
        // rank shift may just be someone joining or leaving, not a real pass.
        val KEY_LAST_KNOWN_RANK          = intPreferencesKey("last_known_rank")
        val KEY_LAST_KNOWN_MEMBER_COUNT  = intPreferencesKey("last_known_member_count")

        // An invite code from a link or QR that we could not act on yet,
        // usually because the user had not finished onboarding. Held here so
        // the invite survives the whole signup flow and is honoured the moment
        // they reach Home, instead of being silently lost.
        val KEY_PENDING_INVITE_CODE      = stringPreferencesKey("pending_invite_code")

        // A property of this phone, not of the account, which is why it is the
        // one key that survives clearAll(). See the note there.
        val KEY_THEME                    = stringPreferencesKey("theme_preference")

        // Metric or imperial. Survives clearAll() for the same reason as the
        // theme: it describes the person holding the phone, not the account.
        val KEY_UNIT_SYSTEM              = stringPreferencesKey("unit_system")

        // When steps last actually reached Firestore. Written only on a
        // successful sync, so "last synced" never claims a run that failed.
        val KEY_LAST_SYNC_TIME           = longPreferencesKey("last_sync_time")

        // Categories the user has switched OFF, not the ones left on. Storing
        // the exclusions means a category added in a later release is on by
        // default rather than silently missing for existing users.
        val KEY_MUTED_CATEGORIES         = stringSetPreferencesKey("muted_notification_categories")

        // Minutes from midnight, so a time survives locale and timezone changes
        // that a formatted string would not.
        val KEY_QUIET_START_MINUTE       = intPreferencesKey("quiet_hours_start_minute")
        val KEY_QUIET_END_MINUTE         = intPreferencesKey("quiet_hours_end_minute")
    }

    val userId: Flow<String?>  = context.dataStore.data.map { it[KEY_USER_ID] }
    val userName: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }
    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_ONBOARDED] ?: false }
    val isHealthConnectGranted: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_HEALTH_CONNECT_GRANTED] ?: false
    }
    val activeGroupId: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_GROUP_ID] }
    val stepBaselineDate: Flow<String?>  = context.dataStore.data.map { it[KEY_STEP_BASELINE_DATE] }
    val stepBaselineValue: Flow<Long>    = context.dataStore.data.map {
        it[KEY_STEP_BASELINE_VALUE]?.toLongOrNull() ?: 0L
    }
    val lastNotificationTime: Flow<Long> = context.dataStore.data.map {
        it[KEY_LAST_NOTIFICATION_TIME] ?: 0L
    }
    val lastKnownGroupTotal: Flow<Long> = context.dataStore.data.map {
        it[KEY_LAST_KNOWN_GROUP_TOTAL] ?: 0L
    }
    val consecutiveIgnoredNotifications: Flow<Int> = context.dataStore.data.map {
        it[KEY_CONSECUTIVE_IGNORED] ?: 0
    }
    val themePreference: Flow<ThemePreference> = context.dataStore.data.map {
        ThemePreference.fromName(it[KEY_THEME])
    }

    suspend fun saveThemePreference(preference: ThemePreference) {
        context.dataStore.edit { it[KEY_THEME] = preference.name }
    }

    /**
     * Metric or imperial, for display only.
     *
     * Defaults to metric rather than to the device locale. A locale-derived
     * default would silently change what a stored number appears to say when
     * someone travels or switches language, and metric is right for the primary
     * market anyway.
     */
    val unitSystem: Flow<UnitSystem> = context.dataStore.data.map {
        UnitSystem.fromName(it[KEY_UNIT_SYSTEM])
    }

    suspend fun saveUnitSystem(system: UnitSystem) {
        context.dataStore.edit { it[KEY_UNIT_SYSTEM] = system.name }
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

    /** Names of the notification categories the user has switched off. */
    val mutedNotificationCategories: Flow<Set<String>> = context.dataStore.data.map {
        it[KEY_MUTED_CATEGORIES] ?: emptySet()
    }

    suspend fun setCategoryMuted(categoryName: String, muted: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_MUTED_CATEGORIES] ?: emptySet()
            prefs[KEY_MUTED_CATEGORIES] =
                if (muted) current + categoryName else current - categoryName
        }
    }

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

    suspend fun saveLastKnownGroupTotal(total: Long) {
        context.dataStore.edit { it[KEY_LAST_KNOWN_GROUP_TOTAL] = total }
    }

    /** Called when a notification is posted, assumed ignored until proven otherwise. */
    suspend fun incrementIgnoredNotifications() {
        context.dataStore.edit {
            it[KEY_CONSECUTIVE_IGNORED] = (it[KEY_CONSECUTIVE_IGNORED] ?: 0) + 1
        }
    }

    /** Called when the user opens the app from a notification. */
    suspend fun resetIgnoredNotifications() {
        context.dataStore.edit { it[KEY_CONSECUTIVE_IGNORED] = 0 }
    }

    /** 0 means "never recorded", no comparison is made on the first sync. */
    val lastKnownRank: Flow<Int> = context.dataStore.data.map { it[KEY_LAST_KNOWN_RANK] ?: 0 }
    val lastKnownMemberCount: Flow<Int> = context.dataStore.data.map {
        it[KEY_LAST_KNOWN_MEMBER_COUNT] ?: 0
    }

    suspend fun saveLeaderboardPosition(rank: Int, memberCount: Int) {
        context.dataStore.edit {
            it[KEY_LAST_KNOWN_RANK] = rank
            it[KEY_LAST_KNOWN_MEMBER_COUNT] = memberCount
        }
    }

    val pendingInviteCode: Flow<String?> = context.dataStore.data.map {
        it[KEY_PENDING_INVITE_CODE]
    }

    suspend fun savePendingInviteCode(code: String) {
        context.dataStore.edit { it[KEY_PENDING_INVITE_CODE] = code }
    }

    suspend fun clearPendingInviteCode() {
        context.dataStore.edit { it.remove(KEY_PENDING_INVITE_CODE) }
    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { it[KEY_USER_ID] = userId }
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[KEY_USER_NAME] = name }
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

    suspend fun saveLastNotificationTime(timestamp: Long) {
        context.dataStore.edit { it[KEY_LAST_NOTIFICATION_TIME] = timestamp }
    }

    /**
     * Wipes everything account-scoped, on sign out and on account deletion.
     *
     * The theme is deliberately carried across. It describes how this phone
     * should look, not who is signed in, and having the app snap back to the
     * system theme the moment someone signs out reads as a bug rather than a
     * reset.
     */
    suspend fun clearAll() {
        val theme = context.dataStore.data.map { it[KEY_THEME] }.first()
        val units = context.dataStore.data.map { it[KEY_UNIT_SYSTEM] }.first()
        context.dataStore.edit { prefs ->
            prefs.clear()
            theme?.let { prefs[KEY_THEME] = it }
            units?.let { prefs[KEY_UNIT_SYSTEM] = it }
        }
    }
}