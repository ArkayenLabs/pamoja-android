package com.pamoja.app.di

import com.pamoja.app.data.remote.firebase.FirebaseAuthRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseGroupRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseStepRepositoryImpl
import com.pamoja.app.data.remote.firebase.FirebaseUserRepositoryImpl
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.domain.repository.StepRepository
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
}