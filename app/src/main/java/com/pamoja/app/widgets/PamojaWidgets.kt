package com.pamoja.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.pamoja.app.MainActivity
import com.pamoja.app.R
import com.pamoja.app.domain.model.SignalKind
import com.pamoja.app.ui.kit.LightKitColors
import com.pamoja.app.ui.kit.initialsOf
import com.pamoja.app.util.SmartNotificationHelper
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.absoluteValue

/*
 * The three home-screen widgets, on the Lilac kit. Their class names are the
 * ones the terracotta widgets shipped with and must stay: renaming a provider
 * silently removes it from every home screen it was placed on.
 *
 *  - GroupProgressWidgetProvider: the group card.
 *  - TodayStepsWidgetProvider: "You today", lime when someone cheered you.
 *  - WalkHookWidgetProvider: the group's streak.
 *
 * Text stays text (system sans, not the kit's downloadable fonts, which a
 * widget cannot load) so it follows the user's font size and TalkBack reads
 * it. Only the faces are bitmaps.
 */

class GroupProgressWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PamojaWidgetRenderer.onUpdate(context)
        ids.forEach { manager.updateAppWidget(it, PamojaWidgetRenderer.groupViews(context)) }
    }
}

class TodayStepsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PamojaWidgetRenderer.onUpdate(context)
        ids.forEach { manager.updateAppWidget(it, PamojaWidgetRenderer.todayViews(context)) }
    }
}

class WalkHookWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PamojaWidgetRenderer.onUpdate(context)
        ids.forEach { manager.updateAppWidget(it, PamojaWidgetRenderer.streakViews(context)) }
    }
}

/**
 * Nudge and Cheer back, tapped on a widget. Not exported: only the widgets'
 * own PendingIntents reach it, so no other app can send a nudge as this user.
 * The recipients are worked out here from the stored snapshot, never taken
 * from the intent.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = SignalKind.fromWire(intent.getStringExtra(EXTRA_KIND).orEmpty()) ?: return
        val snapshot = WidgetStore.read(context) ?: return
        val group = snapshot.group ?: return
        if (group.groupId != intent.getStringExtra(EXTRA_GROUP)) return
        val today = LocalDate.now().toString()

        val to = when (kind) {
            SignalKind.NUDGE -> groupCard(snapshot)?.toNudge.orEmpty()
            SignalKind.CHEER -> (youToday(snapshot) as? YouTodayModel.Cheered)?.toCheerBack.orEmpty()
        }
        if (to.isEmpty()) return

        // Shown as sent at once, as on Home; the refresh after the send puts
        // back the truth if it was refused.
        val sentToday = snapshot.signalsDate == today
        val updated = snapshot.copy(
            signalsDate = today,
            cheersFrom = if (sentToday) snapshot.cheersFrom else emptyList(),
            nudged = (if (sentToday) snapshot.nudged else emptySet()) + if (kind == SignalKind.NUDGE) to else emptyList(),
            cheeredBack = (if (sentToday) snapshot.cheeredBack else emptySet()) + if (kind == SignalKind.CHEER) to else emptyList(),
        )
        WidgetStore.write(context, updated)
        PamojaWidgetRenderer.updateAll(context)
        WidgetSignalWorker.enqueue(context, kind, group.groupId, snapshot.userId, to, today)
    }

    companion object {
        const val EXTRA_KIND = "com.pamoja.app.widgets.KIND"
        const val EXTRA_GROUP = "com.pamoja.app.widgets.GROUP"
    }
}

internal object PamojaWidgetRenderer {
    /** How stale a snapshot may get before a widget's own update asks for a read. */
    private const val STALE_MS = 60 * 60 * 1000L

    fun onUpdate(context: Context) {
        val snapshot = WidgetStore.read(context)
        if (snapshot == null || System.currentTimeMillis() - snapshot.refreshedAt > STALE_MS) {
            enqueueRefresh(context, delaySeconds = 0)
        }
    }

    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return PROVIDERS.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        fun update(provider: Class<*>, views: () -> RemoteViews) {
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) views().let { v -> ids.forEach { manager.updateAppWidget(it, v) } }
        }
        update(GroupProgressWidgetProvider::class.java) { groupViews(context) }
        update(TodayStepsWidgetProvider::class.java) { todayViews(context) }
        update(WalkHookWidgetProvider::class.java) { streakViews(context) }
    }

    fun groupViews(base: Context, snapshot: WidgetSnapshot? = WidgetStore.read(base)): RemoteViews {
        val context = ContextCompat.getContextForLanguage(base)
        val views = RemoteViews(context.packageName, R.layout.widget_group_progress)
        val card = snapshot?.let { groupCard(it) }
        views.setViewVisibility(R.id.widget_group_content, if (card == null) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_group_empty, if (card == null) View.VISIBLE else View.GONE)
        bindOpenApp(context, views, R.id.widget_group_root, card?.groupId)
        if (card == null) return views

        views.setTextViewText(R.id.widget_group_title, card.groupName)
        if (card.streak > 0) {
            views.setTextViewText(R.id.widget_group_streak, weeks(context, card.streak))
            views.setViewVisibility(R.id.widget_group_streak, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_group_streak, View.GONE)
        }

        FACE_SLOTS.forEachIndexed { index, (slot, image, name) ->
            val face = card.faces.getOrNull(index)
            val isOverflow = face == null && index == card.faces.size && card.overflow > 0
            when {
                face != null -> {
                    val label = if (face.isYou) context.getString(R.string.home_you) else firstName(face.name)
                    views.setViewVisibility(slot, View.VISIBLE)
                    views.setImageViewBitmap(image, WidgetFaces.face(context, face.userId, face.name, dimmed = !face.walked, walked = face.walked))
                    views.setContentDescription(
                        image,
                        context.getString(if (face.walked) R.string.home_face_walked_desc else R.string.home_face_not_walked_desc, label),
                    )
                    views.setTextViewText(name, label)
                }
                isOverflow -> {
                    views.setViewVisibility(slot, View.VISIBLE)
                    views.setImageViewBitmap(image, WidgetFaces.more(context, card.overflow))
                    views.setContentDescription(image, context.resources.getQuantityString(R.plurals.widget_more_members, card.overflow, card.overflow))
                    views.setTextViewText(name, "")
                }
                // Fewer than six: the empty slots still take their share of
                // the row, so a small group spreads out rather than bunching.
                else -> views.setViewVisibility(slot, View.INVISIBLE)
            }
        }

        views.setProgressBar(R.id.widget_group_progress, 100, card.progressPercent, false)
        views.setTextViewText(R.id.widget_group_total, context.getString(R.string.widget_steps_of, format(card.steps), format(card.target)))
        views.setTextViewText(
            R.id.widget_group_days,
            if (card.reached) context.getString(R.string.home_goal_reached)
            else context.resources.getQuantityString(R.plurals.group_days_left, card.daysLeft, card.daysLeft),
        )

        if (card.waiting.isEmpty()) {
            views.setViewVisibility(R.id.widget_group_waiting_row, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_group_waiting_row, View.VISIBLE)
            views.setTextViewText(R.id.widget_group_waiting, notWalkedYet(context, card.waiting))
            val canNudge = card.toNudge.isNotEmpty()
            views.setViewVisibility(R.id.widget_group_nudge, if (canNudge) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_group_nudged, if (canNudge) View.GONE else View.VISIBLE)
            bindSignal(context, views, R.id.widget_group_nudge, SignalKind.NUDGE, card.groupId)
        }
        return views
    }

    fun todayViews(base: Context, snapshot: WidgetSnapshot? = WidgetStore.read(base)): RemoteViews {
        val context = ContextCompat.getContextForLanguage(base)
        val views = RemoteViews(context.packageName, R.layout.widget_today_steps)
        val model = snapshot?.let { youToday(it) } ?: YouTodayModel.Steps(steps = null, usual = null)
        views.setViewVisibility(R.id.widget_today_steps_state, if (model is YouTodayModel.Steps) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_today_cheer_state, if (model is YouTodayModel.Cheered) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_today_connect_state, if (model is YouTodayModel.Connect) View.VISIBLE else View.GONE)
        bindOpenApp(context, views, R.id.widget_today_root, snapshot?.group?.groupId)

        when (model) {
            is YouTodayModel.Cheered -> {
                val name = firstName(model.from.name)
                views.setImageViewBitmap(R.id.widget_today_cheer_face, WidgetFaces.face(context, model.from.userId, model.from.name))
                views.setTextViewText(
                    R.id.widget_today_cheer_text,
                    if (model.others == 0) context.getString(R.string.home_cheer_from_one, name)
                    else context.resources.getQuantityString(R.plurals.home_cheer_from_many, model.others, name, model.others),
                )
                val canCheer = model.toCheerBack.isNotEmpty()
                views.setViewVisibility(R.id.widget_today_cheer_back, if (canCheer) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_today_cheered, if (canCheer) View.GONE else View.VISIBLE)
                snapshot?.group?.let { bindSignal(context, views, R.id.widget_today_cheer_back, SignalKind.CHEER, it.groupId) }
            }
            is YouTodayModel.Steps -> {
                val steps = model.steps
                views.setTextViewText(R.id.widget_today_steps, if (steps == null) "–" else format(steps))
                val usual = model.usual
                when {
                    steps == null -> {
                        views.setTextViewText(R.id.widget_today_usual, context.getString(R.string.widget_open_to_sync))
                        views.setViewVisibility(R.id.widget_today_bar, View.GONE)
                    }
                    usual == null || usual <= 0 -> {
                        views.setViewVisibility(R.id.widget_today_usual, View.GONE)
                        views.setViewVisibility(R.id.widget_today_bar, View.GONE)
                    }
                    else -> {
                        views.setTextViewText(R.id.widget_today_usual, context.getString(R.string.home_your_usual, format(usual)))
                        views.setProgressBar(R.id.widget_today_bar, 100, ((steps * 100) / usual).coerceIn(0, 100).toInt(), false)
                    }
                }
            }
            YouTodayModel.Connect -> Unit
        }
        return views
    }

    fun streakViews(base: Context, snapshot: WidgetSnapshot? = WidgetStore.read(base)): RemoteViews {
        val context = ContextCompat.getContextForLanguage(base)
        val views = RemoteViews(context.packageName, R.layout.widget_walk_hook)
        bindOpenApp(context, views, R.id.widget_hook_root, snapshot?.group?.groupId)
        when (val model = snapshot?.let { streakTile(it) } ?: StreakModel.NoGroup) {
            is StreakModel.Streak -> {
                views.setTextViewText(R.id.widget_hook_number, format(model.weeks.toLong()))
                views.setTextViewText(
                    R.id.widget_hook_label,
                    context.resources.getQuantityString(R.plurals.widget_streak_label, model.weeks),
                )
            }
            is StreakModel.Progress -> {
                views.setTextViewText(R.id.widget_hook_number, NumberFormat.getPercentInstance().format(model.percent / 100.0))
                views.setTextViewText(R.id.widget_hook_label, context.getString(R.string.widget_goal_share))
            }
            StreakModel.NoGroup -> {
                views.setViewVisibility(R.id.widget_hook_number, View.GONE)
                views.setTextViewText(R.id.widget_hook_label, context.getString(R.string.widget_no_group_body))
            }
        }
        return views
    }

    private fun weeks(context: Context, count: Int) =
        context.resources.getQuantityString(R.plurals.home_streak_weeks, count, count)

    /** The same sentence Home uses under today's faces. */
    private fun notWalkedYet(context: Context, names: List<String>): String = when (names.size) {
        1 -> context.getString(R.string.home_not_walked_one, names[0])
        2 -> context.getString(R.string.home_not_walked_two, names[0], names[1])
        else -> context.resources.getQuantityString(R.plurals.home_not_walked_many, names.size - 2, names[0], names[1], names.size - 2)
    }

    private fun firstName(name: String): String = name.trim().substringBefore(' ').ifBlank { name }

    private fun bindOpenApp(context: Context, views: RemoteViews, viewId: Int, groupId: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!groupId.isNullOrBlank()) putExtra(SmartNotificationHelper.EXTRA_GROUP_ID, groupId)
        }
        val pending = PendingIntent.getActivity(
            context,
            viewId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(viewId, pending)
    }

    private fun bindSignal(context: Context, views: RemoteViews, viewId: Int, kind: SignalKind, groupId: String) {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            putExtra(WidgetActionReceiver.EXTRA_KIND, kind.wire)
            putExtra(WidgetActionReceiver.EXTRA_GROUP, groupId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(viewId, pending)
    }

    private fun format(value: Long): String = NumberFormat.getIntegerInstance().format(value)

    private val PROVIDERS = listOf(
        GroupProgressWidgetProvider::class.java,
        TodayStepsWidgetProvider::class.java,
        WalkHookWidgetProvider::class.java,
    )

    private val FACE_SLOTS = listOf(
        Triple(R.id.widget_face_slot_1, R.id.widget_face_1, R.id.widget_face_name_1),
        Triple(R.id.widget_face_slot_2, R.id.widget_face_2, R.id.widget_face_name_2),
        Triple(R.id.widget_face_slot_3, R.id.widget_face_3, R.id.widget_face_name_3),
        Triple(R.id.widget_face_slot_4, R.id.widget_face_4, R.id.widget_face_name_4),
        Triple(R.id.widget_face_slot_5, R.id.widget_face_5, R.id.widget_face_name_5),
        Triple(R.id.widget_face_slot_6, R.id.widget_face_6, R.id.widget_face_name_6),
    )
}

/**
 * Faces drawn to bitmaps the way the kit's Avatar draws them: the cached
 * photo, or initials on the member's own colour; greyed at half strength
 * until they have walked, and a lime check once they have.
 */
internal object WidgetFaces {
    private const val SIZE_PX = 120
    private const val DIMMED_BACKGROUND = 0xFFDCDAE3.toInt()

    fun face(context: Context, userId: String, name: String, dimmed: Boolean = false, walked: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Room at the bottom right for the check, which the kit draws
        // overhanging the circle.
        val diameter = if (walked) SIZE_PX * 0.86f else SIZE_PX.toFloat()
        val radius = diameter / 2
        val alpha = if (dimmed) 128 else 255

        val palette = LightKitColors.avatarBackgrounds
        val background = if (dimmed) DIMMED_BACKGROUND else palette[userId.hashCode().absoluteValue % palette.size].toArgb()
        canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background; this.alpha = alpha })

        val photo = WidgetStore.faceFile(context, userId).takeIf { it.exists() }
            ?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val scale = diameter / minOf(photo.width, photo.height)
                setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate((diameter - photo.width * scale) / 2, (diameter - photo.height * scale) / 2)
                })
            }
            canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.shader = shader
                this.alpha = alpha
                if (dimmed) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            })
        } else {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = LightKitColors.onAvatar.toArgb()
                this.alpha = alpha
                textSize = diameter * 0.38f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            val baseline = radius - (text.descent() + text.ascent()) / 2
            canvas.drawText(initialsOf(name), radius, baseline, text)
        }

        if (walked) drawCheck(context, canvas)
        return bitmap
    }

    /** "+3" for the members who do not fit. */
    fun more(context: Context, count: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = SIZE_PX / 2f
        canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_track) })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.widget_ink)
            textSize = SIZE_PX * 0.34f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("+$count", r, r - (text.descent() + text.ascent()) / 2, text)
        return bitmap
    }

    private fun drawCheck(context: Context, canvas: Canvas) {
        val badge = SIZE_PX * 0.42f
        val cx = SIZE_PX - badge / 2
        val cy = SIZE_PX - badge / 2
        canvas.drawCircle(cx, cy, badge / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_surface) })
        canvas.drawCircle(cx, cy, badge / 2 - SIZE_PX * 0.035f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_pop) })
        val s = badge * 0.22f
        val path = Path().apply {
            moveTo(cx - s, cy)
            lineTo(cx - s * 0.25f, cy + s * 0.75f)
            lineTo(cx + s * 1.05f, cy - s * 0.7f)
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.widget_on_pop)
            style = Paint.Style.STROKE
            strokeWidth = SIZE_PX * 0.045f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
    }
}
