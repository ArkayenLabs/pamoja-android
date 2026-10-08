package com.pamoja.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone makers whose own battery managers stop apps in the background beyond
 * what stock Android does, each with its own setting to undo it.
 *
 * On these phones, an app that is not allowed to start on its own cannot sync
 * steps or receive a push of either kind: FCM draws even a notification
 * message inside the app's process (NOTIFICATIONS_PLAN.md §0). No API says
 * whether the switch is on, so the card says what to change and trusts the
 * person to do it.
 */
enum class PhoneMaker { ColorOs, Miui, Vivo, Samsung, Huawei }

@Singleton
class BackgroundRestrictions @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * The maker to explain, or null when there is nothing to ask. Samsung's
     * restriction is the standard battery optimisation, which Android does
     * report, so an exempt Samsung phone is not asked.
     */
    fun makerToExplain(): PhoneMaker? {
        val maker = makerOf(Build.MANUFACTURER) ?: return null
        if (maker == PhoneMaker.Samsung && isExempt()) return null
        return maker
    }

    private fun isExempt(): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

    companion object {
        fun makerOf(manufacturer: String?): PhoneMaker? = when (manufacturer?.trim()?.lowercase()) {
            "oppo", "realme", "oneplus" -> PhoneMaker.ColorOs
            "xiaomi", "redmi", "poco" -> PhoneMaker.Miui
            "vivo", "iqoo" -> PhoneMaker.Vivo
            "samsung" -> PhoneMaker.Samsung
            "huawei", "honor" -> PhoneMaker.Huawei
            else -> null
        }

        /**
         * This app's own App info page. Deliberately not the makers' hidden
         * auto-start screens: their class names change between versions and
         * newer ones refuse outside apps, while App info exists on all of
         * them and holds each one's switches (the card names which).
         */
        fun openAppInfo(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
}
