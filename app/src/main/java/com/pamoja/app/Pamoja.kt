package com.pamoja.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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
    }
}