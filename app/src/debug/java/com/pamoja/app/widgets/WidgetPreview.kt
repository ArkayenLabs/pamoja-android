package com.pamoja.app.widgets

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlin.math.roundToInt

/**
 * Debug-only: the widgets' real RemoteViews applied into ordinary views on a
 * plain "wallpaper", so they can be reviewed without a launcher. Dark mode is
 * a night configuration, which is how a launcher resolves them too.
 */
object WidgetPreview {
    sealed interface Row {
        data class Group(val snapshot: WidgetSnapshot) : Row
        data class Tiles(val today: WidgetSnapshot, val streak: WidgetSnapshot) : Row
    }

    @Composable
    fun Board(dark: Boolean, rows: List<Row>) {
        val base = LocalContext.current
        val context = if (dark) base.night() else base
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(if (dark) Color(0xFF0F1020) else Color(0xFFE4E0D8))
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            rows.forEach { row ->
                when (row) {
                    is Row.Group -> Widget(context, Modifier.fillMaxWidth().height(236.dp)) {
                        PamojaWidgetRenderer.groupViews(context, row.snapshot)
                    }
                    is Row.Tiles -> androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Widget(context, Modifier.size(156.dp)) { PamojaWidgetRenderer.todayViews(context, row.today) }
                        Widget(context, Modifier.size(156.dp)) { PamojaWidgetRenderer.streakViews(context, row.streak) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Widget(context: Context, modifier: Modifier, views: () -> RemoteViews) {
        AndroidView(
            modifier = modifier,
            factory = { FrameLayout(it) },
            update = { frame ->
                frame.removeAllViews()
                frame.addView(views().apply(context, frame))
            },
        )
    }

    /**
     * Writes the three widgets as transparent PNGs for the widget picker
     * (`android:previewImage`), at [Board]'s sizes and this device's density.
     * Run with `--ez exportWidgets true` on GroupPreviewActivity once with the
     * device in light mode and once in dark (`adb shell cmd uimode night
     * yes|no`): RemoteViews.apply builds its own context, so it follows the
     * device's night mode, not a configuration context. Dark files get
     * `_night`; they go to res/drawable-night-nodpi without it.
     */
    fun export(context: Context, snapshot: WidgetSnapshot, dir: File) {
        dir.mkdirs()
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val suffix = if (night) "_night" else ""
        write(context, 360, 236, PamojaWidgetRenderer.groupViews(context, snapshot), File(dir, "widget_preview_group$suffix.png"))
        write(context, 156, 156, PamojaWidgetRenderer.todayViews(context, snapshot), File(dir, "widget_preview_today$suffix.png"))
        write(context, 156, 156, PamojaWidgetRenderer.streakViews(context, snapshot), File(dir, "widget_preview_streak$suffix.png"))
    }

    private fun write(context: Context, widthDp: Int, heightDp: Int, views: RemoteViews, file: File) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        val view = views.apply(context, FrameLayout(context))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun Context.night(): Context = createConfigurationContext(
        Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        },
    )
}
