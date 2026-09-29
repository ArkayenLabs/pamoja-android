package com.pamoja.app.widgets

import android.content.Context
import android.content.res.Configuration
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

    private fun Context.night(): Context = createConfigurationContext(
        Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        },
    )
}
