package com.pamoja.app.ui.home

/**
 * The notification primer, at most twice. Android 11+ lets an app show its
 * system prompt again after one denial; only a second denial is permanent.
 * So: [First] at the first cheer or nudge sent or received, and if the system
 * prompt was then denied, [Second] once more at the strongest moment, a high
 * five received, never in the same visit as the first. Then never again.
 * Whether Android would still show the prompt (shouldShowRequestPermissionRationale)
 * is checked on screen, where the Activity is.
 */
enum class NotificationAsk { First, Second }

internal fun nextNotificationAsk(
    firstShown: Boolean,
    secondShown: Boolean,
    cheerReceived: Boolean,
    askedThisVisit: Boolean,
): NotificationAsk? = when {
    !firstShown -> NotificationAsk.First
    cheerReceived && !secondShown && !askedThisVisit -> NotificationAsk.Second
    else -> null
}
