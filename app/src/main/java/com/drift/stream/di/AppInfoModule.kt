package com.drift.stream.di

import com.drift.stream.data.AppInfoProviderImpl
import com.drift.stream.domain.repository.AppInfoProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppInfoModule {
    @Binds
    @Singleton
    fun bindAppInfoProvider(impl: AppInfoProviderImpl): AppInfoProvider
}
