package com.drift.stream.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val BACKUP_FORMAT = "drift-backup"
const val BACKUP_VERSION = 1
const val BACKUP_MAX_BYTES = 64 * 1024 * 1024 // 64 MiB

sealed interface ExportScope {
    data class Profile(
        val profileId: Long,
    ) : ExportScope

    data object All : ExportScope
}

@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val backupVersion: Int = BACKUP_VERSION,
    val appVersion: String,
    val createdAt: Long,
    val profiles: List<BackupProfile>,
)

@Serializable
data class BackupProfile(
    val name: String,
    val avatar: BackupAvatar,
    val library: List<BackupLibraryItem>,
    val progress: List<BackupProgressItem>,
    val searchHistory: List<BackupSearchItem>? = null,
    val settings: List<BackupSettingsStore>,
    val addons: List<BackupAddonItem>? = null,
)

@Serializable
data class BackupAvatar(
    val colorIndex: Int,
    val customColor: Int? = null,
    val emoji: String? = null,
    val photoJpegBase64: String? = null,
)

@Serializable
data class BackupLibraryItem(
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val inWatchlist: Boolean,
    val addedAt: Long,
    val listStatus: String,
)

@Serializable
data class BackupProgressItem(
    val key: String,
    val metaId: String,
    val videoId: String,
    val type: String,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long,
)

@Serializable
data class BackupSearchItem(
    val query: String,
    val lastUsedAt: Long,
)

@Serializable
data class BackupSettingsStore(
    val store: String,
    val entries: List<BackupPrefEntry>,
)

@Serializable
data class BackupPrefEntry(
    val key: String,
    val type: String,
    val value: JsonElement,
)

@Serializable
data class BackupAddonItem(
    val transportUrl: String,
    val id: String,
    val name: String,
    val version: String,
    val manifestJson: String,
    val enabled: Boolean,
    val position: Int,
    val source: String,
    val installedAt: Long,
)
