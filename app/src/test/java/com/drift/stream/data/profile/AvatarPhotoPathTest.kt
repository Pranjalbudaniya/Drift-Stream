package com.drift.stream.data.profile

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AvatarPhotoPathTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun AF1_avatarPhotoFile_matchesStoreFileOf() {
        val root = tempFolder.root
        val store = AvatarPhotoStoreImpl(root)
        listOf(1L, 7L, 123L).forEach { id ->
            assertEquals(store.fileOf(id), avatarPhotoFile(root, id))
        }
    }

    @Test
    fun AF2_avatarPhotoFile_usesAvatarsDirIdAndJpgExtension() {
        val root = tempFolder.root
        val file = avatarPhotoFile(root, 7L)
        assertEquals(File(File(root, "avatars"), "7.jpg"), file)
    }
}
