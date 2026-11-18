package com.pamoja.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
    /** One of [UnusedAppRestrictionsConstants], corrected by [effectiveStatus]. */
    suspend fun status(): Int = effectiveStatus(
        compat = compatStatus(),
        sdk = Build.VERSION.SDK_INT,
        exempt = {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                runCatching { context.packageManager.isAutoRevokeWhitelisted }.getOrDefault(false)
        },
    )

    /** AndroidX's answer; ERROR if Android would not say. */
    private suspend fun compatStatus(): Int {
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
        /**
         * AndroidX only trusts the status when the App info screen belongs to
         * an app holding PACKAGE_VERIFICATION_AGENT. On a vivo (Android 15,
         * checked 18 Nov 2026) Settings does not, so AndroidX answers
         * FEATURE_NOT_AVAILABLE while App info plainly shows "Pause app
         * activity if unused" and hibernation is on. From Android 12 the
         * platform's own exemption flag is the honest answer in that case.
         */
        fun effectiveStatus(compat: Int, sdk: Int, exempt: () -> Boolean): Int {
            val unsure = compat == UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE ||
                compat == UnusedAppRestrictionsConstants.ERROR
            if (sdk < Build.VERSION_CODES.S || !unsure) return compat
            return if (exempt()) UnusedAppRestrictionsConstants.DISABLED else UnusedAppRestrictionsConstants.API_31
        }

        /**
         * Android requires this to be started for a result. From Android 12
         * AndroidX itself just opens App info, but throws first when it
         * judges the feature missing (see [effectiveStatus]), so the page is
         * opened directly there.
         */
        fun settingsIntent(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            } else {
                IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
            }

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
