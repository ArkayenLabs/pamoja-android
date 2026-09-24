package com.pamoja.app.ui.adventure

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.pamoja.app.R
import com.pamoja.app.domain.model.Adventure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat

/** A deliberate, anonymous keepsake. No screenshots, identities or source records. */
internal suspend fun prepareTrailCompletionShare(context: Context, adventure: Adventure): Intent =
    withContext(Dispatchers.IO) {
        require(adventure.completed && adventure.earnedFinish) { "Finish must be earned" }
        val locale = context.resources.configuration.locales[0]
        val steps = NumberFormat.getIntegerInstance(locale).format(adventure.target)
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val file = File(File(context.cacheDir, "trail-cards").apply { mkdirs() }, "completion.png")
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(23, 45, 39))
            val gold = Color.rgb(245, 188, 101)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; strokeWidth = 5f }
            canvas.drawLine(110f, 290f, 970f, 290f, ink)
            repeat(5) { index -> canvas.drawCircle(110f + index * 215f, 290f, 17f, ink) }
            fun label(value: String, top: Float, size: Float, color: Int, bold: Boolean = false): Float {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    textSize = size
                    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                }
                val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, 860)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
                canvas.save()
                canvas.translate(110f, top)
                layout.draw(canvas)
                canvas.restore()
                return top + layout.height
            }
            label("Pamoja", 120f, 44f, gold, true)
            val titleBottom = label(context.getString(R.string.adventure_title), 400f, 72f, Color.WHITE, true)
            val finishBottom = label(context.getString(R.string.adventure_completion_title), titleBottom + 45f, 48f, gold)
            label(context.getString(R.string.trail_share_goal, steps), finishBottom + 60f, 54f, Color.WHITE, true)
            label(context.getString(R.string.trail_share_footer), 1110f, 34f, Color.rgb(185, 206, 193))
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.trailcards", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.trail_share_caption))
            clipData = ClipData.newUri(context.contentResolver, "Pamoja", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
