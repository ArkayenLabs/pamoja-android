package com.pamoja.app.data.local.health

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient

/**
 * Whether Health Connect can be used, and if not, what would fix it. On
 * Android 13 and below it is a separate Play app, so an outdated copy is a
 * state of its own: Google's guidance is to send the person to update it,
 * which an "install it" message would get wrong.
 */
enum class HealthConnectAvailability { Available, NeedsUpdate, NotInstalled }

/** Kept apart from [HealthConnectReader] so it can be tested on the JVM. */
fun healthConnectAvailabilityOf(sdkStatus: Int): HealthConnectAvailability = when (sdkStatus) {
    HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NeedsUpdate
    else -> HealthConnectAvailability.NotInstalled
}

const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * Health Connect's Play listing, with the onboarding hint Google's docs give
 * for the update case. The web listing if Play itself will not open.
 */
fun openHealthConnectListing(context: Context) {
    val market = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri(),
    ).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(
        Intent.ACTION_VIEW,
        "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
}
