package com.pamoja.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Android's app hibernation (Android 12+): an app nobody opens for a few
 * months loses its runtime permissions, its background jobs and its pushes,
 * and gets none of them back on its own. Background syncs do not count as use.
 * That is the quiet member who walks and never opens Pamoja, whose steps then
 * stop reaching the group without anyone noticing.
 *
 * https://developer.android.com/topic/performance/app-hibernation
 */
@Singleton
class AppHibernation @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** One of [UnusedAppRestrictionsConstants]; ERROR if Android would not say. */
    suspend fun status(): Int {
        val future = runCatching {
            PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
        }.getOrElse { return UnusedAppRestrictionsConstants.ERROR }
        return suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    cont.resume(
                        runCatching { future.get() }.getOrDefault(UnusedAppRestrictionsConstants.ERROR),
                    )
                },
                Runnable::run,
            )
        }
    }

    companion object {
        /** Android requires this to be started for a result. */
        fun settingsIntent(context: Context): Intent =
            IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)

        /**
         * Ask once, only someone in a group (their steps are what is at stake),
         * only after Home's tour so the two never stack, and only where
         * hibernation is on. API_30 and its backport only reset runtime
         * permissions, which on Android 11 and below Pamoja has none of that
         * matter, so there is nothing worth sending anyone to Settings for.
         * DISABLED, FEATURE_NOT_AVAILABLE and ERROR never ask.
         */
        fun shouldAsk(status: Int?, askedBefore: Boolean?, inAGroup: Boolean, tourSeen: Boolean?): Boolean =
            inAGroup &&
                tourSeen == true &&
                askedBefore == false &&
                status == UnusedAppRestrictionsConstants.API_31
    }
}
