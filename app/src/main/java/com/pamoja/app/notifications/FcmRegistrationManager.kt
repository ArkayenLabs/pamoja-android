package com.pamoja.app.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.pamoja.app.BuildConfig
import com.pamoja.app.data.local.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps this signed-in installation registered with Pamoja's trusted backend.
 *
 * The callable owns the Firestore write. Push registrations are delivery
 * credentials, not profile data, so mobile clients never receive direct write
 * access. Owner-scoped reads exist only for the account data export.
 *
 * Besides the address, the registration tells the server what it needs to
 * send well: that this build draws tray pushes from its own strings
 * ([DELIVERY_VERSION]), the phone's time zone, and its quiet hours, so the
 * server's reminders arrive at a sensible local time and never at night.
 */
@Singleton
class FcmRegistrationManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val messaging: FirebaseMessaging,
    private val userPreferences: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var installationId: String? = null

    /** When this process last told the server the app was opened. */
    @Volatile
    private var lastOpenReportMs = 0L

    /** An open seen before FCM gave an installation id; sent with the first upload. */
    @Volatile
    private var openPending = false

    private val started = AtomicBoolean(false)

    @Volatile
    private var previousUid: String? = null

    private val authListener = FirebaseAuth.AuthStateListener { state ->
        val currentUid = state.currentUser?.uid
        val signedOut = previousUid != null && currentUid == null
        previousUid = currentUid

        when {
            currentUid != null -> messaging.register()
                .addOnFailureListener { Log.w(TAG, "FCM registration refresh failed") }

            signedOut -> messaging.unregister()
                .addOnFailureListener { Log.w(TAG, "FCM unregister failed") }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        previousUid = auth.currentUser?.uid
        auth.addAuthStateListener(authListener)
    }

    /** Called by [PamojaFirebaseMessagingService] whenever FCM registers. */
    fun onRegistered(installationId: String) {
        if (auth.currentUser == null || installationId.isBlank()) return
        this.installationId = installationId
        upload(installationId, opened = openPending)
        openPending = false
    }

    /**
     * Tells the server the person opened the app, which is what resets its
     * count of unopened reminders (it goes quiet after 8 in a row). Called on
     * every resume; sent at most every [OPEN_REPORT_GAP_MS]. A registration
     * alone cannot say this: background step syncs start the process too.
     */
    fun reportOpened(nowMs: Long = System.currentTimeMillis()) {
        if (auth.currentUser == null || nowMs - lastOpenReportMs < OPEN_REPORT_GAP_MS) return
        lastOpenReportMs = nowMs
        val known = installationId
        if (known != null) upload(known, opened = true) else openPending = true
    }

    /** Sends the registration again, after quiet hours change. */
    fun refresh() {
        if (auth.currentUser == null) return
        val known = installationId
        if (known != null) {
            upload(known)
        } else {
            messaging.register()
                .addOnFailureListener { Log.w(TAG, "FCM registration refresh failed") }
        }
    }

    private fun upload(installationId: String, opened: Boolean = false) {
        scope.launch {
            functions.getHttpsCallable(REGISTER_FUNCTION)
                .call(
                    mapOf(
                        "opened" to opened,
                        "installationId" to installationId,
                        "appVersion" to BuildConfig.VERSION_NAME,
                        "delivery" to DELIVERY_VERSION,
                        "timeZone" to ZoneId.systemDefault().id,
                        "quietStartMinute" to userPreferences.quietHoursStartMinute.first(),
                        "quietEndMinute" to userPreferences.quietHoursEndMinute.first(),
                    )
                )
                .addOnFailureListener {
                    // Never log the FID. It is a device-scoped delivery identifier.
                    Log.w(TAG, "FCM installation upload failed")
                }
        }
    }

    /** Best-effort cleanup while an authenticated session still exists. */
    fun onUnregistered(installationId: String) {
        if (auth.currentUser == null || installationId.isBlank()) return

        functions.getHttpsCallable(UNREGISTER_FUNCTION)
            .call(mapOf("installationId" to installationId))
            .addOnFailureListener { Log.w(TAG, "FCM installation cleanup failed") }
    }

    private companion object {
        const val TAG = "FcmRegistration"

        /**
         * 2: this build has the push_* strings and records tray pushes in
         * Updates, so the server may send notification messages and schedule
         * reminders for it. Older builds send nothing here and keep getting
         * data-only pushes, which their own code draws.
         * 3: it also reports opens ([reportOpened]), so the server may stop
         * scheduled reminders nobody opens. Never applied to a build that
         * cannot report, or it would go quiet for good.
         */
        const val DELIVERY_VERSION = 3

        const val OPEN_REPORT_GAP_MS = 6 * 60 * 60 * 1000L
        const val REGISTER_FUNCTION = "registerPushInstallation"
        const val UNREGISTER_FUNCTION = "unregisterPushInstallation"
    }
}
