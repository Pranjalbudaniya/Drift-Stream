package com.drift.stream.data.local.db.dao

import com.drift.stream.data.local.db.entity.ProfileAddonPrefEntity

class FakeProfileAddonPrefsDao(
    private val fakeAddonDao: FakeAddonDao? = null,
) : ProfileAddonPrefsDao {
    private val prefs = mutableMapOf<Triple<Long, Long, String>, ProfileAddonPrefEntity>()

    override suspend fun upsert(pref: ProfileAddonPrefEntity) {
        prefs[Triple(pref.profileId, pref.ownerProfileId, pref.transportUrl)] = pref
        fakeAddonDao?.setPref(pref.profileId, pref.ownerProfileId, pref.transportUrl, pref.enabled)
    }

    override suspend fun deleteForAddon(
        ownerProfileId: Long,
        transportUrl: String,
    ) {
        prefs.keys.removeAll { it.second == ownerProfileId && it.third == transportUrl }
        fakeAddonDao?.clearPrefForAddon(ownerProfileId, transportUrl)
    }

    override suspend fun deleteAllForProfile(profileId: Long) {
        prefs.keys.removeAll { it.first == profileId || it.second == profileId }
        fakeAddonDao?.clearPrefsForProfile(profileId)
    }

    fun isEnabled(
        profileId: Long,
        ownerProfileId: Long,
        transportUrl: String,
    ): Boolean = prefs[Triple(profileId, ownerProfileId, transportUrl)]?.enabled ?: false
}
