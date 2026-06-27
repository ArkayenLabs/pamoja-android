package com.pamoja.app.di

import com.pamoja.app.data.analytics.FirebaseAnalyticsManager
import com.pamoja.app.domain.analytics.AnalyticsManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the [AnalyticsManager] interface to its
 * Firebase implementation.
 *
 * Separated from [RepositoryModule] to keep analytics wiring isolated.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    @Singleton
    abstract fun bindAnalyticsManager(
        impl: FirebaseAnalyticsManager
    ): AnalyticsManager
}
