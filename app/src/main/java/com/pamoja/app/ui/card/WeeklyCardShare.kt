package com.pamoja.app.ui.card

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.pamoja.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes the weekly card to the cache and opens the share sheet.
 *
 * The card's QR code and link go to the Play listing, never to the group's
 * invite. The card is made to be posted where anyone can see it, and an
 * invite code is the group ID: the rules let anyone holding it join, and it
 * can never be changed. A Play link also never breaks when the invite domain
 * moves, which matters because shared cards live on in chats for years.
 */
internal object WeeklyCardShare {

    /** Play shows "Open" when the app is installed and "Install" when not. The referrer tags the source. */
    fun appLink(): String =
        "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}" +
            "&referrer=utm_source%3Dweekly_card%26utm_medium%3Dshare"

    suspend fun share(context: Context, card: Bitmap, text: String) {
        val uri = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(directory, "weekly-card.png")
            // A GraphicsLayer capture can be hardware-backed, which cannot be
            // compressed directly.
            val software = if (card.config == Bitmap.Config.HARDWARE) {
                card.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                card
            }
            file.outputStream().use { software.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            // ClipData as well as the extra, so the grant reaches the chosen app.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
