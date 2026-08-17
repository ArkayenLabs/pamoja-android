package com.pamoja.app.ui.components

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

/**
 * A wall-clock time for "this figure was last true at".
 *
 * Uses the platform formatter rather than a fixed pattern, so it follows the
 * device's 12 or 24 hour setting. A hardcoded "HH:mm" would show 19:12 to
 * someone whose phone says 7:12 PM everywhere else.
 *
 * Returns null when there has never been a sync, which is a different statement
 * from "synced at some unknown time" and should render as no timestamp at all
 * rather than as the epoch.
 */
fun formatSyncTime(context: Context, epochMillis: Long): String? {
    if (epochMillis <= 0L) return null
    return DateFormat.getTimeFormat(context).format(Date(epochMillis))
}
