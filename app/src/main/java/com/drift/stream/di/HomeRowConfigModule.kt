package com.drift.stream.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.drift.stream.data.profile.PersonalStore
import com.drift.stream.data.profile.ProfileDataStores
import com.drift.stream.data.profile.ProfileScopedDataStore
import com.drift.stream.data.repository.HomeRowConfigRepositoryImpl
import com.drift.stream.data.repository.HomeRowsDataStore
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.HomeRowConfigRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface HomeRowConfigModule {
    @Binds
    @Singleton
    fun bindHomeRowConfigRepository(impl: HomeRowConfigRepositoryImpl): HomeRowConfigRepository

    companion object {
        @Provides
        @Singleton
        @HomeRowsDataStore
        fun provideHomeRowsDataStore(
            stores: ProfileDataStores,
            activeProfileProvider: ActiveProfileProvider,
        ): DataStore<Preferences> =
            ProfileScopedDataStore(
                kind = PersonalStore.HOME_ROWS,
                stores = stores,
                active = activeProfileProvider,
            )
    }
}
