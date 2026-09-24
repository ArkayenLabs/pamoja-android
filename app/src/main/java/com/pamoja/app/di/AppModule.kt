package com.pamoja.app.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.pamoja.app.BuildConfig
import com.pamoja.app.data.local.preferences.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CirclePreviewEnabled

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DayOnePlanningEnabled

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance(FIREBASE_FUNCTIONS_REGION)

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @TogetherTrailEnabled
    fun provideTogetherTrailEnabled(): Boolean = BuildConfig.TOGETHER_TRAIL_ENABLED

    @Provides
    @CirclePreviewEnabled
    fun provideCirclePreviewEnabled(): Boolean = BuildConfig.CIRCLE_PREVIEW_ENABLED

    @Provides
    @DayOnePlanningEnabled
    fun provideDayOnePlanningEnabled(): Boolean = BuildConfig.DAY_ONE_PLANNING_ENABLED

    /** Avatars only. See storage.rules, which denies everything else. */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(
        @ApplicationContext context: Context
    ): FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences = UserPreferences(context)
}

private const val FIREBASE_FUNCTIONS_REGION = "asia-south1"
