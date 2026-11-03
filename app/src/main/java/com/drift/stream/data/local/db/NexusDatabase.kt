package com.drift.stream.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.drift.stream.data.local.db.dao.AddonDao
import com.drift.stream.data.local.db.dao.DownloadDao
import com.drift.stream.data.local.db.dao.LibraryDao
import com.drift.stream.data.local.db.dao.MetaCacheDao
import com.drift.stream.data.local.db.dao.ProgressDao
import com.drift.stream.data.local.db.dao.SearchHistoryDao
import com.drift.stream.data.local.db.entity.AddonEntity
import com.drift.stream.data.local.db.entity.DownloadEntity
import com.drift.stream.data.local.db.entity.LibraryItemEntity
import com.drift.stream.data.local.db.entity.MetaCacheEntity
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
    ],
    version = 4,
    exportSchema = true,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao

    abstract fun libraryDao(): LibraryDao

    abstract fun progressDao(): ProgressDao

    abstract fun metaCacheDao(): MetaCacheDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun downloadDao(): DownloadDao
}
