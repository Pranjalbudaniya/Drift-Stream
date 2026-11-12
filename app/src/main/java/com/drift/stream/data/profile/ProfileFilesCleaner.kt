package com.drift.stream.data.profile

import com.drift.stream.data.player.PlayerTrace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileFilesCleaner
    @Inject
    constructor(
        private val profileDataStores: ProfileDataStores,
        private val avatarPhotoStore: AvatarPhotoStore,
    ) {
        suspend fun deleteFiles(profileId: Long) {
            try {
                profileDataStores.delete(profileId)
            } catch (expected: Exception) {
                PlayerTrace.log("profile.delete_error")
                throw expected
            }
            avatarPhotoStore.delete(profileId)
        }
    }
