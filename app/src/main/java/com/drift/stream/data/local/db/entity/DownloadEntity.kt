package com.drift.stream.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "downloads",
    primaryKeys = ["profileId", "id"],
    indices = [
        Index(value = ["metaId", "videoId"]),
        Index(value = ["state"]),
    ],
)
data class DownloadEntity(
    val id: String,
    val kind: String,
    val url: String?,
    val headersJson: String?,
    val infoHash: String?,
    val trackersJson: String?,
    val fileIdx: Int?,
    val metaId: String,
    val type: String,
    val title: String,
    val metaName: String?,
    val posterUrl: String?,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val fileNameHint: String?,
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val state: String,
    val pauseReason: String?,
    val failure: String?,
    val filePath: String?,
    val supportsResume: Boolean,
    val etag: String?,
    val lastModified: String?,
    val allowMetered: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val galleryUri: String? = null,
    val galleryState: String = "NONE",
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1L,
)
