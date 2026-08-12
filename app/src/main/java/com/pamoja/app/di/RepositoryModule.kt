package com.pamoja.app.di

import com.pamoja.app.data.remote.firebase.FirebaseAuthRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseGroupRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseStepRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseUserRepositoryImpl
import com.pamoja.app.data.repository.FreeOnlySubscriptionRepository
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.domain.repository.StepRepository
import com.pamoja.app.domain.repository.SubscriptionRepository
import com.pamoja.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: FirebaseAuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: FirebaseUserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(
        impl: FirebaseGroupRepositoryImpl
    ): GroupRepository

    @Binds
    @Singleton
    abstract fun bindStepRepository(
        impl: FirebaseStepRepositoryImpl
    ): StepRepository

    /**
     * Free for everyone until RevenueCat is wired, which waits on the Play
     * merchant chain. Swapping the implementation here is the whole change.
     */
    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        impl: FreeOnlySubscriptionRepository
    ): SubscriptionRepository
}