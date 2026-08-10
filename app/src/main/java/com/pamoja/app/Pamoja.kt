package com.pamoja.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PamojaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Development crashes are deliberate half the time, and they would sit
        // in the same dashboard used to decide whether a release is healthy.
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        initializeAppCheck()
    }

    /**
     * App Check attests that requests come from the genuine app binary.
     *
     * This matters more here than in most apps: the client talks to Firestore
     * directly with no backend in between, so without it anyone holding the API
     * key from the APK can script requests against the database and burn the
     * quota. It is a cost control as much as a security one.
     *
     * Installing the provider is safe on its own. Nothing is rejected until
     * enforcement is switched on per-service in the Firebase console, which is
     * the point at which the debug token below must already be registered or
     * development builds will start failing every Firestore call.
     */
    private fun initializeAppCheck() {
        FirebaseApp.initializeApp(this)

        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) {
                // Prints a token to logcat on first run. Register it under
                // App Check, Apps, Manage debug tokens.
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        )
    }
}