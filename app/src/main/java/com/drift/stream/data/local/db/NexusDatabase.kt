package com.drift.stream.data.local.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.drift.stream.data.local.db.dao.AddonDao
import com.drift.stream.data.local.db.dao.DownloadDao
import com.drift.stream.data.local.db.dao.LibraryDao
import com.drift.stream.data.local.db.dao.MetaCacheDao
import com.drift.stream.data.local.db.dao.ProfileAddonPrefsDao
import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.data.local.db.dao.ProgressDao
import com.drift.stream.data.local.db.dao.SearchHistoryDao
import com.drift.stream.data.local.db.entity.AddonEntity
import com.drift.stream.data.local.db.entity.DownloadEntity
import com.drift.stream.data.local.db.entity.LibraryItemEntity
import com.drift.stream.data.local.db.entity.MetaCacheEntity
import com.drift.stream.data.local.db.entity.ProfileAddonPrefEntity
import com.drift.stream.data.local.db.entity.ProfileEntity
import com.drift.stream.data.local.db.entity.SearchHistoryEntity
import com.drift.stream.data.local.db.entity.SkipSegmentCacheEntity
import com.drift.stream.data.local.db.entity.WatchProgressEntity

@Database(
    entities = [
        AddonEntity::class,
        WatchProgressEntity::class,
        LibraryItemEntity::class,
        SearchHistoryEntity::class,
        SkipSegmentCacheEntity::class,
        MetaCacheEntity::class,
        DownloadEntity::class,
        ProfileEntity::class,
        ProfileAddonPrefEntity::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
    exportSchema = true,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao

    abstract fun libraryDao(): LibraryDao

    abstract fun progressDao(): ProgressDao

    abstract fun metaCacheDao(): MetaCacheDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun downloadDao(): DownloadDao

    abstract fun profileDao(): ProfileDao

    abstract fun profileAddonPrefsDao(): ProfileAddonPrefsDao
}
