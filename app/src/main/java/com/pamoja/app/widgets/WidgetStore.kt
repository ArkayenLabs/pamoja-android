package com.pamoja.app.widgets

import android.content.Context
import androidx.core.content.edit
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Where the widgets' [WidgetSnapshot] lives between redraws: one JSON value in
 * the preferences file the old widgets used, plus a small cache of members'
 * photos, already cut round, since a widget cannot download anything itself.
 *
 * Holds only what the three widgets draw: the shown group's name, goal and
 * streak, its members' names, photos and step figures (all of it already on
 * their membership documents, which every member can read), and this user's
 * own steps and today's cheers and nudges.
 */
internal object WidgetStore {
    private const val PREFS = "pamoja_widget_snapshot"
    private const val KEY = "snapshot_v2"
    private const val FACES_DIR = "widget_faces"

    fun read(context: Context): WidgetSnapshot? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun write(context: Context, snapshot: WidgetSnapshot) {
        // commit() rather than apply(): the caller redraws straight after, and
        // a redraw from a separate process-start must see this value.
        prefs(context).edit(commit = true) {
            clear() // also drops the keys the terracotta widgets stored
            putString(KEY, encode(snapshot).toString())
        }
    }

    /** Signing out: nothing about the last account may stay on the home screen. */
    fun clear(context: Context) {
        prefs(context).edit(commit = true) { clear() }
        facesDir(context).deleteRecursively()
    }

    fun faceFile(context: Context, userId: String): File =
        File(facesDir(context), userId.filter { it.isLetterOrDigit() } + ".png")

    fun facesDir(context: Context): File = File(context.cacheDir, FACES_DIR)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encode(s: WidgetSnapshot) = JSONObject().apply {
        put("userId", s.userId)
        s.group?.let { g ->
            put("group", JSONObject().apply {
                put("groupId", g.groupId)
                put("name", g.name)
                put("weeklyTarget", g.weeklyTarget)
                put("weekStartDay", g.weekStartDay)
                put("planningTimeZone", g.planningTimeZone)
                put("streakWeeks", g.streakWeeks)
                put("streakThroughWeek", g.streakThroughWeek)
            })
        }
        put("members", JSONArray().apply {
            s.members.forEach { m ->
                put(JSONObject().apply {
                    put("userId", m.userId)
                    put("displayName", m.displayName)
                    put("photoUrl", m.photoUrl)
                    put("weeklySteps", m.weeklySteps)
                    put("weekStart", m.weekStart)
                    put("todaySteps", m.todaySteps)
                    put("todayDate", m.todayDate)
                })
            }
        })
        put("signalsDate", s.signalsDate)
        put("cheersFrom", JSONArray(s.cheersFrom))
        put("cheeredBack", JSONArray(s.cheeredBack.toList()))
        put("nudged", JSONArray(s.nudged.toList()))
        s.todaySteps?.let { put("todaySteps", it) }
        put("stepsDate", s.stepsDate)
        s.usualSteps?.let { put("usualSteps", it) }
        put("refreshedAt", s.refreshedAt)
    }

    private fun decode(o: JSONObject) = WidgetSnapshot(
        userId = o.getString("userId"),
        group = o.optJSONObject("group")?.let { g ->
            Group(
                groupId = g.getString("groupId"),
                name = g.optString("name"),
                weeklyTarget = g.optInt("weeklyTarget"),
                weekStartDay = g.optString("weekStartDay"),
                planningTimeZone = g.optString("planningTimeZone"),
                streakWeeks = g.optInt("streakWeeks"),
                streakThroughWeek = g.optString("streakThroughWeek"),
            )
        },
        members = o.optJSONArray("members").objects().map { m ->
            Membership(
                userId = m.getString("userId"),
                displayName = m.optString("displayName"),
                photoUrl = m.optString("photoUrl"),
                weeklySteps = m.optLong("weeklySteps"),
                weekStart = m.optString("weekStart"),
                todaySteps = m.optLong("todaySteps"),
                todayDate = m.optString("todayDate"),
            )
        },
        signalsDate = o.optString("signalsDate"),
        cheersFrom = o.optJSONArray("cheersFrom").strings(),
        cheeredBack = o.optJSONArray("cheeredBack").strings().toSet(),
        nudged = o.optJSONArray("nudged").strings().toSet(),
        todaySteps = if (o.has("todaySteps")) o.getLong("todaySteps") else null,
        stepsDate = o.optString("stepsDate"),
        usualSteps = if (o.has("usualSteps")) o.getLong("usualSteps") else null,
        refreshedAt = o.optLong("refreshedAt"),
    )

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
