package com.drift.stream.data.local.db.dao

import com.drift.stream.data.local.db.entity.AddonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAddonDao : AddonDao {
    private val addons = MutableStateFlow<Map<Pair<Long, String>, AddonEntity>>(emptyMap())
    private val profileNames = MutableStateFlow<Map<Long, String>>(mapOf(1L to "Me"))
    private val prefs = MutableStateFlow<Map<Triple<Long, Long, String>, Boolean>>(emptyMap())

    fun setProfileName(
        profileId: Long,
        name: String,
    ) {
        val current = profileNames.value.toMutableMap()
        current[profileId] = name
        profileNames.value = current
    }

    fun setPref(
        profileId: Long,
        ownerProfileId: Long,
        transportUrl: String,
        enabled: Boolean,
    ) {
        val current = prefs.value.toMutableMap()
        current[Triple(profileId, ownerProfileId, transportUrl)] = enabled
        prefs.value = current
    }

    fun clearPrefForAddon(
        ownerProfileId: Long,
        transportUrl: String,
    ) {
        val current = prefs.value.toMutableMap()
        current.keys.removeAll { it.second == ownerProfileId && it.third == transportUrl }
        prefs.value = current
    }

    fun clearPrefsForProfile(profileId: Long) {
        val current = prefs.value.toMutableMap()
        current.keys.removeAll { it.first == profileId || it.second == profileId }
        prefs.value = current
    }

    override fun observeAll(profileId: Long): Flow<List<AddonEntity>> =
        addons.map { map ->
            map.values
                .filter { it.profileId == profileId }
                .sortedBy { it.position }
        }

    override suspend fun getAll(profileId: Long): List<AddonEntity> =
        addons.value.values
            .filter { it.profileId == profileId }
            .sortedBy { it.position }

    suspend fun getAll(): List<AddonEntity> = getAll(1L)

    override suspend fun get(
        profileId: Long,
        transportUrl: String,
    ): AddonEntity? = addons.value[Pair(profileId, transportUrl)]

    suspend fun get(transportUrl: String): AddonEntity? = get(1L, transportUrl)

    override suspend fun upsert(entity: AddonEntity) {
        val current = addons.value.toMutableMap()
        current[Pair(entity.profileId, entity.transportUrl)] = entity
        addons.value = current
    }

    override suspend fun updateAll(entities: List<AddonEntity>) {
        val current = addons.value.toMutableMap()
        for (entity in entities) {
            current[Pair(entity.profileId, entity.transportUrl)] = entity
        }
        addons.value = current
    }

    override suspend fun delete(
        profileId: Long,
        transportUrl: String,
    ) {
        val current = addons.value.toMutableMap()
        current.remove(Pair(profileId, transportUrl))
        addons.value = current
    }

    override suspend fun setEnabled(
        profileId: Long,
        transportUrl: String,
        enabled: Boolean,
    ) {
        val current = addons.value.toMutableMap()
        val key = Pair(profileId, transportUrl)
        val existing = current[key] ?: return
        current[key] = existing.copy(enabled = enabled)
        addons.value = current
    }

    override suspend fun setShared(
        profileId: Long,
        transportUrl: String,
        shared: Boolean,
    ): Int {
        val current = addons.value.toMutableMap()
        val key = Pair(profileId, transportUrl)
        val existing = current[key] ?: return 0
        current[key] = existing.copy(shared = shared)
        addons.value = current
        return 1
    }

    override fun observeHiddenSharedCount(profileId: Long): Flow<Int> =
        addons.map { map ->
            val ownUrls =
                map.values
                    .filter { it.profileId == profileId }
                    .map { it.transportUrl }
                    .toSet()
            map.values
                .filter { it.shared && it.profileId != profileId && it.transportUrl in ownUrls }
                .map { it.transportUrl }
                .distinct()
                .size
        }

    override fun observeSharedByOthers(profileId: Long): Flow<List<SharedAddonRow>> =
        addons.map { map ->
            val allSharedByOthers =
                map.values
                    .filter { it.shared && it.profileId != profileId }

            val ownUrls =
                map.values
                    .filter { it.profileId == profileId }
                    .map { it.transportUrl }
                    .toSet()

            allSharedByOthers
                .filter { it.transportUrl !in ownUrls }
                .groupBy { it.transportUrl }
                .map { (_, list) -> list.minBy { it.profileId } }
                .sortedWith(compareBy({ it.profileId }, { it.position }))
                .map { addon ->
                    val ownerName = profileNames.value[addon.profileId] ?: "Profile ${addon.profileId}"
                    val enabledForMe = prefs.value[Triple(profileId, addon.profileId, addon.transportUrl)] ?: false
                    SharedAddonRow(
                        addon = addon,
                        ownerName = ownerName,
                        enabledForMe = enabledForMe,
                    )
                }
        }

    override suspend fun deleteAllForProfile(profileId: Long) {
        val current = addons.value.toMutableMap()
        current.entries.removeAll { it.key.first == profileId }
        addons.value = current
    }
}
