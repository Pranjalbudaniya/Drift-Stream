package com.drift.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.drift.stream.data.local.db.entity.LibraryItemEntity
import kotlinx.coroutines.flow.Flow

data class LibraryStatusTuple(
    val metaId: String,
    val listStatus: String,
)

@Dao
interface LibraryDao {
    @Query("SELECT metaId, listStatus FROM library_items WHERE profileId = :profileId AND inWatchlist = 1")
    fun observeListStatuses(profileId: Long): Flow<List<LibraryStatusTuple>>

    @Query("SELECT * FROM library_items WHERE profileId = :profileId AND inWatchlist = 1")
    fun observeWatchlist(profileId: Long): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE profileId = :profileId AND metaId = :metaId")
    suspend fun get(
        profileId: Long,
        metaId: String,
    ): LibraryItemEntity?

    @Query("SELECT * FROM library_items WHERE profileId = :profileId")
    suspend fun getAllForProfile(profileId: Long): List<LibraryItemEntity>

    @Upsert
    suspend fun upsert(entity: LibraryItemEntity)

    @Query("DELETE FROM library_items WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)
}
