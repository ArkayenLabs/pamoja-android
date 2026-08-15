package com.pamoja.app.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Swallows a second click that arrives too soon after the first.
 *
 * Disabling a button while its work runs is not enough on its own. There is a
 * gap between the tap and the state that disables it: the handler starts a
 * coroutine, the coroutine sets a flag, the flag recomposes. Two fast taps can
 * both land inside that gap, and the button looks correct the whole time.
 *
 * The cost is not theoretical here. Two taps on "Send code" is two real SMS
 * messages, billed, and a step closer to Firebase's rate limit. Two taps on
 * "Create group" is two groups, one of which the user did not want and cannot
 * delete.
 *
 * Deliberately not a debounce that delays the first click. The first click
 * fires immediately, which is what keeps the button feeling instant; only the
 * repeat inside the window is dropped.
 *
 * Uses elapsedRealtime rather than currentTimeMillis so a clock change, or a
 * timezone shift while travelling, cannot make the window behave strangely.
 */
@Composable
fun rememberSingleClick(
    windowMs: Long = DEFAULT_WINDOW_MS,
    onClick: () -> Unit,
): () -> Unit {
    // A plain holder rather than Compose state: nothing renders from this, so
    // writing it must not schedule a recomposition.
    val lastClickAt = remember { longArrayOf(0L) }
    // So the returned lambda always calls the newest onClick, rather than the
    // one captured on the composition that created it.
    val currentOnClick by rememberUpdatedState(onClick)

    return remember(windowMs) {
        {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickAt[0] >= windowMs) {
                lastClickAt[0] = now
                currentOnClick()
            }
        }
    }
}

/**
 * Long enough to cover a double tap and the recomposition behind it, short
 * enough that a genuine second attempt is never refused.
 */
private const val DEFAULT_WINDOW_MS = 700L
