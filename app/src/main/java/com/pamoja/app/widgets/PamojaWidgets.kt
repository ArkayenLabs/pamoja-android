package com.pamoja.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pamoja.app.MainActivity
import com.pamoja.app.R
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.WeekWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.time.LocalDate
import com.pamoja.app.util.SmartNotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "pamoja_widget_snapshot"
private const val KEY_TODAY = "today_steps"
private const val KEY_WEEK = "week_steps"
private const val KEY_GROUP_NAME = "group_name"
private const val KEY_GROUP_ID = "group_id"
private const val KEY_GROUP_STEPS = "group_steps"
private const val KEY_GROUP_GOAL = "group_goal"
private const val KEY_MEMBER_COUNT = "member_count"
private const val KEY_DAYS_LEFT = "days_left"
private const val KEY_HAS_DATA = "has_data"
private const val KEY_SNAPSHOT_DAY = "snapshot_day"
private const val KEY_GROUP_COUNT = "group_count"
private const val KEY_ACTIVE_GROUP_COUNT = "active_group_count"
private const val KEY_GROUP_AVERAGE = "group_average"
private const val KEY_GROUP_SUMMARY = "group_summary"

private data class WidgetSnapshot(
    val hasData: Boolean,
    val todaySteps: Long,
    val weekSteps: Long,
    val groupName: String,
    val groupId: String,
    val groupSteps: Long,
    val groupGoal: Int,
    val memberCount: Int,
    val daysLeft: Int,
    val groupCount: Int,
    val activeGroupCount: Int,
    val groupAverage: Int,
    val groupSummary: String,
)

/** Stores only the small aggregate snapshot needed by user-added home widgets. */
@Singleton
class PamojaWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun publish(todaySteps: Long, weekSteps: Long, groups: List<Group>) {
        val currentGroups = groups.map { group ->
            val steps = if (WeekWindow.isCurrent(group)) {
                group.weeklySteps.coerceAtLeast(0L)
            } else 0L
            group to steps
        }
        val activeCount = currentGroups.count { (_, steps) -> steps > 0L }
        val average = currentGroups
            .map { (group, steps) ->
                if (group.weeklyTarget <= 0) 0
                else ((steps * 100L) / group.weeklyTarget).coerceIn(0L, 100L).toInt()
            }
            .let { values -> if (values.isEmpty()) 0 else values.sum() / values.size }
        val summary = currentGroups
            .sortedByDescending { (group, steps) ->
                if (group.weeklyTarget <= 0) 0L else (steps * 100L) / group.weeklyTarget
            }
            .take(3)
            .joinToString("  •  ") { (group, steps) ->
                val percent = if (group.weeklyTarget <= 0) 0L
                else ((steps * 100L) / group.weeklyTarget).coerceIn(0L, 999L)
                "${group.name} $percent%"
            }

        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HAS_DATA, true)
            .putString(KEY_SNAPSHOT_DAY, LocalDate.now().toString())
            .putLong(KEY_TODAY, todaySteps.coerceAtLeast(0L))
            .putLong(KEY_WEEK, weekSteps.coerceAtLeast(0L))
            .putInt(KEY_GROUP_COUNT, currentGroups.size)
            .putInt(KEY_ACTIVE_GROUP_COUNT, activeCount)
            .putInt(KEY_GROUP_AVERAGE, average)
            .putString(KEY_GROUP_SUMMARY, summary)

        // Show one concrete shared goal, rather than an average across unrelated groups.
        val focus = currentGroups.filter { (group, steps) -> group.weeklyTarget > steps }
            .minByOrNull { (group, steps) -> group.weeklyTarget - steps }
            ?: currentGroups.firstOrNull()
        val focusGroup = focus?.first
        if (focusGroup == null) {
            editor
                .remove(KEY_GROUP_NAME)
                .remove(KEY_GROUP_ID)
                .remove(KEY_GROUP_STEPS)
                .remove(KEY_GROUP_GOAL)
                .remove(KEY_MEMBER_COUNT)
                .remove(KEY_DAYS_LEFT)
        } else {
            editor
                .putString(KEY_GROUP_NAME, focusGroup.name)
                .putString(KEY_GROUP_ID, focusGroup.groupId)
                .putLong(KEY_GROUP_STEPS, requireNotNull(focus).second)
                .putInt(KEY_GROUP_GOAL, focusGroup.weeklyTarget.coerceAtLeast(0))
                .putInt(KEY_MEMBER_COUNT, focusGroup.memberCount.coerceAtLeast(0))
                .putInt(KEY_DAYS_LEFT, WeekWindow.daysLeftIn(focusGroup))
        }

        editor.apply()
        PamojaWidgetRenderer.updateAll(context)
    }

    fun clear() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        PamojaWidgetRenderer.updateAll(context)
    }
}

class TodayStepsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { PamojaWidgetRenderer.updateToday(context, manager, it) }
    }
}

class GroupProgressWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { PamojaWidgetRenderer.updateGroup(context, manager, it) }
    }
}

class WalkHookWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { PamojaWidgetRenderer.updateHook(context, manager, it) }
    }
}

internal object PamojaWidgetRenderer {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        updateProvider(context, manager, TodayStepsWidgetProvider::class.java, ::updateToday)
        updateProvider(context, manager, GroupProgressWidgetProvider::class.java, ::updateGroup)
        updateProvider(context, manager, WalkHookWidgetProvider::class.java, ::updateHook)
    }

    fun updateToday(context: Context, manager: AppWidgetManager, id: Int) {
        manager.updateAppWidget(id, todayViews(context))
    }

    fun todayViews(baseContext: Context): RemoteViews {
        val context = androidx.core.content.ContextCompat.getContextForLanguage(baseContext)
        val snapshot = read(context)
        val views = RemoteViews(context.packageName, R.layout.widget_today_steps)
        views.setTextViewText(R.id.widget_today_label, context.getString(R.string.widget_today_label))
        views.setTextViewText(R.id.widget_today_cta, context.getString(R.string.widget_view_progress))
        views.setTextViewText(
            R.id.widget_today_steps,
            if (snapshot.hasData) format(snapshot.todaySteps) else "—",
        )
        views.setTextViewText(
            R.id.widget_today_hook,
            when {
                !snapshot.hasData -> context.getString(R.string.widget_open_to_sync)
                snapshot.todaySteps == 0L -> context.getString(R.string.widget_first_step_hook)
                snapshot.todaySteps < 3_000L -> context.getString(R.string.widget_small_walk_hook)
                else -> context.getString(R.string.widget_moving_hook)
            },
        )
        bindOpenApp(context, views, R.id.widget_today_root)
        return views
    }

    fun updateGroup(context: Context, manager: AppWidgetManager, id: Int) {
        manager.updateAppWidget(id, groupViews(context))
    }

    fun groupViews(baseContext: Context): RemoteViews {
        val context = androidx.core.content.ContextCompat.getContextForLanguage(baseContext)
        val snapshot = read(context)
        val views = RemoteViews(context.packageName, R.layout.widget_group_progress)
        views.setTextViewText(R.id.widget_group_cta, context.getString(R.string.widget_open_group))
        val hasGroups = snapshot.groupCount > 0
        val remaining = (snapshot.groupGoal.toLong() - snapshot.groupSteps).coerceAtLeast(0L)
        views.setTextViewText(R.id.widget_group_title,
            if (hasGroups) snapshot.groupName else context.getString(R.string.widget_together_label))

        views.setTextViewText(
            R.id.widget_group_count,
            if (hasGroups) format(remaining) else "—",
        )
        views.setTextViewText(
            R.id.widget_group_state,
            if (hasGroups) context.getString(if (remaining == 0L) R.string.widget_goal_reached else R.string.widget_steps_left)
            else context.getString(R.string.widget_no_group_title),
        )
        val progress = if (snapshot.groupGoal > 0) ((snapshot.groupSteps * 100L) / snapshot.groupGoal).coerceIn(0L, 100L).toInt() else 0
        views.setProgressBar(R.id.widget_group_progress, 100, progress, false)
        views.setTextViewText(
            R.id.widget_group_summary,
            if (hasGroups) context.getString(R.string.widget_last_shared_update,
                format(snapshot.groupSteps), format(snapshot.groupGoal.toLong()))
            else context.getString(R.string.widget_no_group_body),
        )
        bindOpenApp(context, views, R.id.widget_group_root, snapshot.groupId.takeIf { hasGroups && it.isNotBlank() })
        return views
    }

    fun updateHook(context: Context, manager: AppWidgetManager, id: Int) {
        manager.updateAppWidget(id, hookViews(context))
    }

    fun hookViews(baseContext: Context): RemoteViews {
        val context = androidx.core.content.ContextCompat.getContextForLanguage(baseContext)
        val snapshot = read(context)
        val message = when {
            !snapshot.hasData -> context.getString(R.string.widget_open_to_sync)
            snapshot.todaySteps == 0L -> context.getString(R.string.widget_first_step_hook)
            snapshot.todaySteps < 1_500L -> context.getString(R.string.widget_small_walk_hook)
            snapshot.todaySteps < 5_000L -> context.getString(R.string.widget_hook_keep_going)
            else -> context.getString(R.string.widget_hook_day_moving)
        }
        val views = RemoteViews(context.packageName, R.layout.widget_walk_hook)
        views.setTextViewText(R.id.widget_hook_cta, context.getString(R.string.widget_view_progress))
        views.setTextViewText(R.id.widget_hook_message, message)
        views.setTextViewText(
            R.id.widget_hook_week,
            if (snapshot.hasData) context.getString(R.string.widget_week_total, format(snapshot.weekSteps))
            else context.getString(R.string.widget_open_pamoja),
        )
        bindOpenApp(context, views, R.id.widget_hook_root)
        return views
    }

    private fun updateProvider(
        context: Context,
        manager: AppWidgetManager,
        provider: Class<out AppWidgetProvider>,
        update: (Context, AppWidgetManager, Int) -> Unit,
    ) {
        manager.getAppWidgetIds(ComponentName(context, provider)).forEach {
            update(context, manager, it)
        }
    }

    private fun bindOpenApp(context: Context, views: RemoteViews, viewId: Int, groupId: String? = null) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (groupId != null) putExtra(SmartNotificationHelper.EXTRA_GROUP_ID, groupId)
        }
        val pending = PendingIntent.getActivity(
            context,
            viewId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(viewId, pending)
    }

    private fun read(context: Context): WidgetSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacyName = prefs.getString(KEY_GROUP_NAME, "").orEmpty()
        val legacySteps = prefs.getLong(KEY_GROUP_STEPS, 0L)
        val legacyGoal = prefs.getInt(KEY_GROUP_GOAL, 0)
        val legacyPercent = if (legacyGoal > 0) {
            ((legacySteps * 100L) / legacyGoal).coerceIn(0L, 999L).toInt()
        } else 0
        return WidgetSnapshot(
            hasData = prefs.getBoolean(KEY_HAS_DATA, false) &&
                prefs.getString(KEY_SNAPSHOT_DAY, null) == LocalDate.now().toString(),
            todaySteps = prefs.getLong(KEY_TODAY, 0L),
            weekSteps = prefs.getLong(KEY_WEEK, 0L),
            groupName = legacyName,
            groupId = prefs.getString(KEY_GROUP_ID, "").orEmpty(),
            groupSteps = legacySteps,
            groupGoal = legacyGoal,
            memberCount = prefs.getInt(KEY_MEMBER_COUNT, 0),
            daysLeft = prefs.getInt(KEY_DAYS_LEFT, 0),
            groupCount = prefs.getInt(KEY_GROUP_COUNT, if (legacyName.isNotBlank()) 1 else 0),
            activeGroupCount = prefs.getInt(
                KEY_ACTIVE_GROUP_COUNT,
                if (legacySteps > 0L) 1 else 0,
            ),
            groupAverage = prefs.getInt(KEY_GROUP_AVERAGE, legacyPercent.coerceAtMost(100)),
            groupSummary = prefs.getString(KEY_GROUP_SUMMARY, "").orEmpty().ifBlank {
                if (legacyName.isBlank()) "" else "$legacyName $legacyPercent%"
            },
        )
    }

    private fun format(value: Long): String = NumberFormat.getIntegerInstance().format(value)
}
