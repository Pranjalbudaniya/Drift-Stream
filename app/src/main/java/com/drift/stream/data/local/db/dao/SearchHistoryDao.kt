package com.drift.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drift.stream.data.local.db.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history WHERE profileId = :profileId ORDER BY lastUsedAt DESC")
    fun observeAll(profileId: Long): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE profileId = :profileId ORDER BY lastUsedAt DESC")
    suspend fun getAll(profileId: Long): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SearchHistoryEntity>)

    @Query("DELETE FROM search_history WHERE profileId = :profileId AND `query` = :query")
    suspend fun delete(
        profileId: Long,
        query: String,
    )

    @Query("DELETE FROM search_history WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)

    @Query("DELETE FROM search_history WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)
}
