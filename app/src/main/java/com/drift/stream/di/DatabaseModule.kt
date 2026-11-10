package com.drift.stream.di

import android.content.Context
import androidx.room.Room
import com.drift.stream.data.local.db.MIGRATION_1_2
import com.drift.stream.data.local.db.MIGRATION_2_3
import com.drift.stream.data.local.db.MIGRATION_3_4
import com.drift.stream.data.local.db.NexusDatabase
import com.drift.stream.data.local.db.RoomTransactionRunner
import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.AddonDao
import com.drift.stream.data.local.db.dao.DownloadDao
import com.drift.stream.data.local.db.dao.LibraryDao
import com.drift.stream.data.local.db.dao.MetaCacheDao
import com.drift.stream.data.local.db.dao.ProfileAddonPrefsDao
import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.data.local.db.dao.ProgressDao
import com.drift.stream.data.local.db.dao.SearchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "drift.db"

    @Provides
    @Singleton
    fun provideNexusDatabase(
        @ApplicationContext context: Context,
    ): NexusDatabase =
        Room
            .databaseBuilder(
                context,
                NexusDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun provideAddonDao(database: NexusDatabase): AddonDao = database.addonDao()

    @Provides
    fun provideLibraryDao(database: NexusDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideProgressDao(database: NexusDatabase): ProgressDao = database.progressDao()

    @Provides
    fun provideMetaCacheDao(database: NexusDatabase): MetaCacheDao = database.metaCacheDao()

    @Provides
    fun provideSearchHistoryDao(database: NexusDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides
    fun provideDownloadDao(database: NexusDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideProfileDao(database: NexusDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideProfileAddonPrefsDao(database: NexusDatabase): ProfileAddonPrefsDao = database.profileAddonPrefsDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(database: NexusDatabase): TransactionRunner = RoomTransactionRunner(database)
}
