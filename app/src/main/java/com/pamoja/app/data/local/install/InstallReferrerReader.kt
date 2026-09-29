package com.pamoja.app.data.local.install

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.util.InviteLink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * The invite code someone installed Pamoja from, if any.
 *
 * Without this an invite is lost on install: the web join page sends a person
 * without the app to Play, and nothing carries the code across, so they land
 * on the Start screen with no group. The join page now puts the code in the
 * Play link's referrer, and Play hands that back here on first launch.
 *
 * Read once per installation. A transient failure (Play's service busy or
 * disconnected) leaves it unread so the next launch tries again.
 */
class InstallReferrerReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {
    suspend fun inviteCodeOnce(): String? {
        if (userPreferences.installReferrerChecked.first()) return null
        val (response, referrer) = read()
        val transient = response == InstallReferrerResponse.SERVICE_UNAVAILABLE ||
            response == InstallReferrerResponse.SERVICE_DISCONNECTED
        if (!transient) userPreferences.setInstallReferrerChecked()
        return InviteLink.codeFromInstallReferrer(referrer)
    }

    private suspend fun read(): Pair<Int, String?> = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(context).build()
        fun finish(response: Int, referrer: String?) {
            runCatching { client.endConnection() }
            if (continuation.isActive) continuation.resume(response to referrer)
        }
        continuation.invokeOnCancellation { runCatching { client.endConnection() } }
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val referrer = if (responseCode == InstallReferrerResponse.OK) {
                        runCatching { client.installReferrer.installReferrer }.getOrNull()
                    } else {
                        null
                    }
                    finish(responseCode, referrer)
                }

                override fun onInstallReferrerServiceDisconnected() {
                    finish(InstallReferrerResponse.SERVICE_DISCONNECTED, null)
                }
            })
        }.onFailure { finish(InstallReferrerResponse.SERVICE_UNAVAILABLE, null) }
    }
}
