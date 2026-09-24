package com.pamoja.app.di

import com.pamoja.app.data.remote.firebase.FirebaseAuthRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseAvatarRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseGroupRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseGroupAccessRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseGroupSponsorshipRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseGroupWeekRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseNextWeekPlanRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseStepRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseUserRepositoryImpl
import com.pamoja.app.data.repository.RevenueCatSubscriptionRepository
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.AvatarRepository
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.domain.repository.GroupAccessRepository
import com.pamoja.app.domain.repository.GroupSponsorshipRepository
import com.pamoja.app.domain.repository.GroupWeekRepository
import com.pamoja.app.domain.repository.NextWeekPlanRepository
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
    abstract fun bindAdventureRepository(
        implementation: com.pamoja.app.data.remote.firebase.FirebaseAdventureRepository,
    ): com.pamoja.app.domain.repository.AdventureRepository

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
    abstract fun bindGroupAccessRepository(
        impl: FirebaseGroupAccessRepositoryImpl
    ): GroupAccessRepository

    @Binds
    @Singleton
    abstract fun bindGroupWeekRepository(
        impl: FirebaseGroupWeekRepositoryImpl
    ): GroupWeekRepository

    @Binds
    @Singleton
    abstract fun bindNextWeekPlanRepository(
        impl: FirebaseNextWeekPlanRepositoryImpl
    ): NextWeekPlanRepository

    @Binds
    @Singleton
    abstract fun bindGroupSponsorshipRepository(
        impl: FirebaseGroupSponsorshipRepositoryImpl
    ): GroupSponsorshipRepository

    @Binds
    @Singleton
    abstract fun bindAvatarRepository(
        impl: FirebaseAvatarRepositoryImpl
    ): AvatarRepository

    @Binds
    @Singleton
    abstract fun bindStepRepository(
        impl: FirebaseStepRepositoryImpl
    ): StepRepository

    /** One implementation for both the explicitly disabled and live paths. */
    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        impl: RevenueCatSubscriptionRepository
    ): SubscriptionRepository
}
