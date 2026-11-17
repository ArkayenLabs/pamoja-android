package com.pamoja.app.ui.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import kotlinx.coroutines.CancellationException

/**
 * Asks Google Play to show its rating card. Called only right after a win (the
 * weekly goal celebration is closed), never on launch and never from a button:
 * Play enforces a hidden quota, so the card may not appear, and a button that
 * sometimes does nothing is a broken experience.
 *
 * No question is asked before it ("Do you like Pamoja?") because Play's
 * guidelines forbid that. Any failure is silent: the user never sees it.
 * https://developer.android.com/guide/playcore/in-app-review
 */
suspend fun requestInAppReview(activity: Activity) {
    try {
        val manager = ReviewManagerFactory.create(activity)
        val info = manager.requestReview()
        manager.launchReview(activity, info)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Play unavailable, quota reached or no network: nothing to show.
    }
}
