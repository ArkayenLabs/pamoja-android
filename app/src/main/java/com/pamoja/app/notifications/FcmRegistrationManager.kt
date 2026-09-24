package com.pamoja.app.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.pamoja.app.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps this signed-in installation registered with Pamoja's trusted backend.
 *
 * The callable owns the Firestore write. Push registrations are delivery
 * credentials, not profile data, so mobile clients never receive direct write
 * access. Owner-scoped reads exist only for the account data export.
 */
@Singleton
class FcmRegistrationManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val messaging: FirebaseMessaging,
) {
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

        functions.getHttpsCallable(REGISTER_FUNCTION)
            .call(
                mapOf(
                    "installationId" to installationId,
                    "appVersion" to BuildConfig.VERSION_NAME,
                )
            )
            .addOnFailureListener {
                // Never log the FID. It is a device-scoped delivery identifier.
                Log.w(TAG, "FCM installation upload failed")
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
        const val REGISTER_FUNCTION = "registerPushInstallation"
        const val UNREGISTER_FUNCTION = "unregisterPushInstallation"
    }
}
