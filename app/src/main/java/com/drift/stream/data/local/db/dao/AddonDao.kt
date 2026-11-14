package com.drift.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.drift.stream.data.local.db.entity.AddonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AddonDao {
    @Query("SELECT * FROM addons WHERE profileId = :profileId ORDER BY position ASC")
    fun observeAll(profileId: Long): Flow<List<AddonEntity>>

    @Query("SELECT * FROM addons WHERE profileId = :profileId ORDER BY position ASC")
    suspend fun getAllForProfile(profileId: Long): List<AddonEntity>

    @Query("SELECT * FROM addons WHERE profileId = :profileId AND transportUrl = :transportUrl")
    suspend fun get(
        profileId: Long,
        transportUrl: String,
    ): AddonEntity?

    @Upsert
    suspend fun upsert(entity: AddonEntity)

    @Update
    suspend fun updateAll(entities: List<AddonEntity>)

    @Query("DELETE FROM addons WHERE profileId = :profileId AND transportUrl = :transportUrl")
    suspend fun delete(
        profileId: Long,
        transportUrl: String,
    )

    @Query("UPDATE addons SET enabled = :enabled WHERE profileId = :profileId AND transportUrl = :transportUrl")
    suspend fun setEnabled(
        profileId: Long,
        transportUrl: String,
        enabled: Boolean,
    )

    @Query("UPDATE addons SET shared = :shared WHERE profileId = :profileId AND transportUrl = :transportUrl")
    suspend fun setShared(
        profileId: Long,
        transportUrl: String,
        shared: Boolean,
    ): Int

    @Query(
        """
        SELECT COUNT(DISTINCT a.transportUrl)
        FROM addons a
        WHERE a.shared = 1
          AND a.profileId != :profileId
          AND EXISTS (
              SELECT 1 FROM addons own
              WHERE own.profileId = :profileId
                AND own.transportUrl = a.transportUrl
          )
        """,
    )
    fun observeHiddenSharedCount(profileId: Long): Flow<Int>

    @Query(
        """
        SELECT 
            a.*,
            p.name AS ownerName,
            COALESCE(pref.enabled, 0) AS enabledForMe
        FROM addons a
        JOIN profiles p ON p.id = a.profileId
        LEFT JOIN profile_addon_prefs pref 
            ON pref.profileId = :profileId 
           AND pref.ownerProfileId = a.profileId 
           AND pref.transportUrl = a.transportUrl
        WHERE a.shared = 1 
          AND a.profileId != :profileId
          AND NOT EXISTS (
              SELECT 1 FROM addons own 
              WHERE own.profileId = :profileId 
                AND own.transportUrl = a.transportUrl
          )
          AND a.profileId = (
              SELECT MIN(a2.profileId) 
              FROM addons a2 
              WHERE a2.transportUrl = a.transportUrl 
                AND a2.shared = 1 
                AND a2.profileId != :profileId
          )
        ORDER BY a.profileId ASC, a.position ASC
        """,
    )
    fun observeSharedByOthers(profileId: Long): Flow<List<SharedAddonRow>>

    @Query("DELETE FROM addons WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)
}
