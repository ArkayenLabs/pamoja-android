package com.pamoja.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pamoja.app.util.BillingIdentity
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

        // A no-op until an API key exists, which needs the Play merchant chain.
        // Called here rather than lazily so that the moment a key is added, the
        // SDK is live on the next launch with nothing else to remember.
        BillingIdentity.configure(this)
        trackBillingIdentity()
    }

    /**
     * Keeps RevenueCat pointed at whoever is signed in.
     *
     * An auth state listener rather than a call in each sign-in path. There are
     * three ways into this app, Google, phone and email, and a fourth would be
     * easy to add and easy to forget here; FirebaseAuth.currentUser is already
     * this app's single source of truth for identity, so the listener is the
     * one place that cannot fall out of step with it. It also fires at launch
     * for a session that was already signed in, which is what re-aliases the
     * SDK after a reinstall.
     *
     * Not doing this is a silent failure, not a crash: entitlements attach to
     * an anonymous per-install id, so a subscription belongs to a phone rather
     * than a person and Restore Purchases finds nothing on a new device while
     * the user is still being billed.
     */
    private fun trackBillingIdentity() {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                BillingIdentity.onSignedIn(user.uid)
            } else {
                BillingIdentity.onSignedOut()
            }
        }
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
     * the point at which the debug token must already be registered or
     * development builds will start failing every Firestore call.
     *
     * Which provider is installed is decided by the build variant rather than
     * here, in `AppCheckProvider.kt`, because the debug factory is a debug-only
     * dependency and so does not exist to be named in a release build.
     */
    private fun initializeAppCheck() {
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installPamojaProvider()
    }
}