package com.drift.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.drift.stream.data.local.db.entity.ProfileAddonPrefEntity

@Dao
interface ProfileAddonPrefsDao {
    @Upsert
    suspend fun upsert(pref: ProfileAddonPrefEntity)

    @Query(
        """
        DELETE FROM profile_addon_prefs
        WHERE ownerProfileId = :ownerProfileId AND transportUrl = :transportUrl
        """,
    )
    suspend fun deleteForAddon(
        ownerProfileId: Long,
        transportUrl: String,
    )

    @Query(
        """
        DELETE FROM profile_addon_prefs
        WHERE profileId = :profileId OR ownerProfileId = :profileId
        """,
    )
    suspend fun deleteAllForProfile(profileId: Long)
}
