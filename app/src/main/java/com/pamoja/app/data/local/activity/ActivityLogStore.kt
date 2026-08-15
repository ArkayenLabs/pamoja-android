package com.pamoja.app.data.local.activity

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pamoja.app.data.local.preferences.dataStore
import com.pamoja.app.domain.model.ActivityItem
import com.pamoja.app.domain.model.NotificationCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every notification this install has shown, newest first.
 *
 * Stored as one JSON array in DataStore rather than in a database. Room would
 * mean a dependency, a schema, and migrations for what is a short, capped,
 * disposable list that never needs querying beyond "give me all of it". The
 * cap is what makes this safe: without it a preferences value would grow
 * without limit and be rewritten in full on every notification.
 *
 * Reads tolerate anything. A value written by a newer build, or a half-written
 * one, yields an empty list rather than an exception: losing the catch-up list
 * is a small harm, and crashing on the way into Home is a large one.
 */
@Singleton
class ActivityLogStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val items: Flow<List<ActivityItem>> = context.dataStore.data.map { prefs ->
        parse(prefs[KEY_ACTIVITY_LOG])
    }

    /** How many are still unread, for the badge on Home. */
    val unreadCount: Flow<Int> = items.map { list -> list.count { !it.isRead } }

    /**
     * Records a notification as shown.
     *
     * Called from the same place that posts to the tray, so the log cannot
     * drift from what the user actually saw.
     */
    suspend fun record(
        category: NotificationCategory,
        title: String,
        body: String,
        groupId: String?,
    ) {
        context.dataStore.edit { prefs ->
            val existing = parse(prefs[KEY_ACTIVITY_LOG])

            val item = ActivityItem(
                id = UUID.randomUUID().toString(),
                category = category,
                title = title,
                body = body,
                shownAt = System.currentTimeMillis(),
                groupId = groupId,
                isRead = false,
            )

            // Newest first, then trimmed. Trimming after prepending means the
            // newest is never the one dropped.
            prefs[KEY_ACTIVITY_LOG] = serialise((listOf(item) + existing).take(MAX_ITEMS))
        }
    }

    /**
     * Marks everything read.
     *
     * All of them at once, on opening the screen, rather than per row. The
     * badge answers "is there anything I have not seen", and once the list has
     * been looked at the answer is no. Per-row read state would leave a badge
     * showing for something already read past.
     */
    suspend fun markAllRead() {
        context.dataStore.edit { prefs ->
            val existing = parse(prefs[KEY_ACTIVITY_LOG])
            if (existing.none { !it.isRead }) return@edit
            prefs[KEY_ACTIVITY_LOG] = serialise(existing.map { it.copy(isRead = true) })
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY_ACTIVITY_LOG) }
    }

    // ── Serialisation ───────────────────────────────────────────────────────

    private fun serialise(items: List<ActivityItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put(FIELD_ID, item.id)
                    put(FIELD_CATEGORY, item.category.name)
                    put(FIELD_TITLE, item.title)
                    put(FIELD_BODY, item.body)
                    put(FIELD_SHOWN_AT, item.shownAt)
                    item.groupId?.let { put(FIELD_GROUP_ID, it) }
                    put(FIELD_READ, item.isRead)
                }
            )
        }
        return array.toString()
    }

    private fun parse(raw: String?): List<ActivityItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                ActivityItem(
                    id = obj.optString(FIELD_ID).ifBlank { return@mapNotNull null },
                    category = NotificationCategory.fromName(obj.optString(FIELD_CATEGORY)),
                    title = obj.optString(FIELD_TITLE),
                    body = obj.optString(FIELD_BODY),
                    shownAt = obj.optLong(FIELD_SHOWN_AT),
                    // optString returns "" for a missing key, which is not the
                    // same as absent and would produce a blank tap target.
                    groupId = obj.optString(FIELD_GROUP_ID).takeIf { it.isNotBlank() },
                    isRead = obj.optBoolean(FIELD_READ),
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY_ACTIVITY_LOG = stringPreferencesKey("activity_log")

        /**
         * Enough to cover a fortnight of a busy group, and small enough that
         * rewriting the whole array on each notification stays cheap.
         */
        const val MAX_ITEMS = 50

        const val FIELD_ID = "id"
        const val FIELD_CATEGORY = "category"
        const val FIELD_TITLE = "title"
        const val FIELD_BODY = "body"
        const val FIELD_SHOWN_AT = "shown_at"
        const val FIELD_GROUP_ID = "group_id"
        const val FIELD_READ = "read"
    }
}
