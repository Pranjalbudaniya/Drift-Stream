package com.drift.stream.data.backup

import com.drift.stream.data.local.db.entity.AddonEntity
import com.drift.stream.data.local.db.entity.LibraryItemEntity
import com.drift.stream.data.local.db.entity.SearchHistoryEntity
import com.drift.stream.data.local.db.entity.WatchProgressEntity
import com.drift.stream.domain.backup.BackupAddonItem
import com.drift.stream.domain.backup.BackupLibraryItem
import com.drift.stream.domain.backup.BackupProgressItem
import com.drift.stream.domain.backup.BackupSearchItem
import com.drift.stream.domain.model.AddonSource

internal class BackupDaoWriter(
    private val daos: BackupDaos,
) {
    suspend fun getLocalLibrary(profileId: Long): List<BackupLibraryItem> =
        daos.library.getAllForProfile(profileId).map {
            BackupLibraryItem(
                it.metaId,
                it.type,
                it.name,
                it.poster,
                it.background,
                it.inWatchlist,
                it.addedAt,
                it.listStatus,
            )
        }

    suspend fun getLocalProgress(profileId: Long): List<BackupProgressItem> =
        daos.progress.getAllForProfile(profileId).map {
            BackupProgressItem(
                it.key,
                it.metaId,
                it.videoId,
                it.type,
                it.season,
                it.episode,
                it.positionMs,
                it.durationMs,
                it.watched,
                it.updatedAt,
            )
        }

    suspend fun getLocalAddons(profileId: Long): List<BackupAddonItem> =
        daos.addon.getAllForProfile(profileId).map {
            BackupAddonItem(
                it.transportUrl,
                it.id,
                it.name,
                it.version,
                it.manifestJson,
                it.enabled,
                it.position,
                it.source.name,
                it.installedAt,
            )
        }

    suspend fun insertLibrary(
        profileId: Long,
        items: List<BackupLibraryItem>,
    ) {
        daos.library.upsertAll(
            items.map {
                LibraryItemEntity(
                    it.metaId,
                    it.type,
                    it.name,
                    it.poster,
                    it.background,
                    it.inWatchlist,
                    it.addedAt,
                    it.listStatus,
                    profileId,
                )
            },
        )
    }

    suspend fun insertProgress(
        profileId: Long,
        items: List<BackupProgressItem>,
    ) {
        daos.progress.upsertAll(
            items.map {
                WatchProgressEntity(
                    it.key,
                    it.metaId,
                    it.videoId,
                    it.type,
                    it.season,
                    it.episode,
                    it.positionMs,
                    it.durationMs,
                    it.watched,
                    it.updatedAt,
                    profileId,
                )
            },
        )
    }

    suspend fun insertSearch(
        profileId: Long,
        items: List<BackupSearchItem>,
    ) {
        daos.searchHistory.upsertAll(
            items.map { SearchHistoryEntity(it.query, it.lastUsedAt, profileId) },
        )
    }

    suspend fun insertAddons(
        profileId: Long,
        items: List<BackupAddonItem>,
    ) {
        for (item in items) {
            daos.addon.upsert(
                AddonEntity(
                    item.transportUrl,
                    item.id,
                    item.name,
                    item.version,
                    item.manifestJson,
                    item.enabled,
                    item.position,
                    AddonSource.valueOf(item.source),
                    item.installedAt,
                    profileId,
                    false,
                ),
            )
        }
    }
}
