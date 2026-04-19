package com.pamoja.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pamoja_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
        val KEY_HEALTH_CONNECT_GRANTED = booleanPreferencesKey("health_connect_granted")
        val KEY_ACTIVE_GROUP_ID = stringPreferencesKey("active_group_id")
    }

    val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }
    val userName: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }
    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_ONBOARDED] ?: false }
    val isHealthConnectGranted: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_HEALTH_CONNECT_GRANTED] ?: false
    }
    val activeGroupId: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_GROUP_ID] }

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

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}