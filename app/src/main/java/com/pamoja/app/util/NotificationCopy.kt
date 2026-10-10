package com.pamoja.app.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import com.pamoja.app.R
import java.text.NumberFormat

/** A string resource named by a push, with its arguments already formatted. */
data class LocalizedText(val key: String, val args: List<String>)

/**
 * The words of every notification, from string resources, in the reader's
 * language. The engine and the server decide what to say; this says it.
 */
object NotificationCopy {

    /** Longer names are cut rather than filling the whole title bar. */
    private const val MAX_GROUP_NAME = 18

    fun render(appContext: Context, notice: LocalNotice): PamojaNotification {
        // In the app's language, not the phone's: below Android 13 a context
        // outside an activity carries the phone's (the widgets do the same).
        val context = ContextCompat.getContextForLanguage(appContext)
        val group = groupLabel(context, notice.message.groupName)
        val (title, body) = when (val message = notice.message) {
            is LocalMessage.StepsIn ->
                context.getString(R.string.notif_first_steps_title) to
                    context.getString(
                        R.string.notif_first_steps_body,
                        NumberFormat.getIntegerInstance(context.resources.configuration.locales[0]).format(message.stepsToday),
                        group,
                    )

            is LocalMessage.AllSet ->
                context.getString(R.string.notif_first_ready_title) to
                    context.getString(R.string.notif_first_ready_body, group)
        }
        return PamojaNotification(
            category = notice.category,
            title = title,
            body = body,
            priority = 90,
            groupId = notice.groupId,
            notificationId = notice.notificationId,
        )
    }

    /**
     * A push's text from the string the server named, or null when this build
     * has no such string (the caller then keeps the server's English).
     *
     * Looked up by name because that is the contract FCM itself uses for
     * title_loc_key; the parser only lets push_* names through, so a payload
     * cannot make the app show an arbitrary resource.
     */
    @SuppressLint("DiscouragedApi")
    fun pushText(appContext: Context, text: LocalizedText?): String? {
        if (text == null) return null
        val context = ContextCompat.getContextForLanguage(appContext)
        val id = context.resources.getIdentifier(text.key, "string", context.packageName)
        if (id == 0) return null
        return runCatching { context.getString(id, *text.args.toTypedArray()) }.getOrNull()
    }

    private fun groupLabel(context: Context, raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        return if (clean.isBlank()) {
            context.getString(R.string.notif_your_group)
        } else {
            clean.take(MAX_GROUP_NAME).trimEnd()
        }
    }
}
