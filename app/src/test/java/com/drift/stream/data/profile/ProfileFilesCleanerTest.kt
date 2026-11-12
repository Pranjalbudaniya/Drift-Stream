package com.drift.stream.data.profile

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProfileFilesCleanerTest {
    @Test
    fun FC1_deleteFiles_removesDataStoresThenPhoto() =
        runTest {
            val profileDataStores = mockk<ProfileDataStores>(relaxed = true)
            val avatarPhotoStore = mockk<AvatarPhotoStore>(relaxed = true)
            val cleaner = ProfileFilesCleaner(profileDataStores, avatarPhotoStore)

            cleaner.deleteFiles(42L)

            coVerifyOrder {
                profileDataStores.delete(42L)
                avatarPhotoStore.delete(42L)
            }
        }

    @Test
    fun FC2_deleteFiles_missingPhoto_isNoOp() =
        runTest {
            val profileDataStores = mockk<ProfileDataStores>(relaxed = true)
            val avatarPhotoStore = mockk<AvatarPhotoStore>()
            coEvery { avatarPhotoStore.delete(any()) } returns Unit

            val cleaner = ProfileFilesCleaner(profileDataStores, avatarPhotoStore)
            cleaner.deleteFiles(99L)
        }
}
