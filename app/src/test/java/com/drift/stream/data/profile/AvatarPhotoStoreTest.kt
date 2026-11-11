package com.drift.stream.data.profile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AvatarPhotoStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createStore(rootDir: File = tempFolder.root): AvatarPhotoStore =
        AvatarPhotoStoreImpl(
            rootDir = rootDir,
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    @Test
    fun PS1_write_createsFileWithBytes() =
        runTest {
            val store = createStore()
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            store.write(profileId = 1L, bytes = bytes)

            val target = store.fileOf(1L)
            assertTrue(target.exists())
            assertEquals(File(File(tempFolder.root, "avatars"), "1.jpg").absolutePath, target.absolutePath)
            assertArrayEquals(bytes, target.readBytes())
        }

    @Test
    fun PS2_rewrite_replacesContent_leavesNoTempFiles() =
        runTest {
            val store = createStore()
            val firstBytes = byteArrayOf(10, 20)
            val secondBytes = byteArrayOf(30, 40, 50)

            store.write(profileId = 2L, bytes = firstBytes)
            store.write(profileId = 2L, bytes = secondBytes)

            val target = store.fileOf(2L)
            assertArrayEquals(secondBytes, target.readBytes())

            val avatarsDir = File(tempFolder.root, "avatars")
            val files = avatarsDir.listFiles().orEmpty()
            assertEquals(1, files.size)
            assertEquals("2.jpg", files.first().name)
        }

    @Test
    fun PS3_delete_removesFile() =
        runTest {
            val store = createStore()
            val bytes = byteArrayOf(1, 2)
            store.write(profileId = 3L, bytes = bytes)
            val target = store.fileOf(3L)
            assertTrue(target.exists())

            store.delete(3L)
            assertFalse(target.exists())
        }

    @Test
    fun PS4_delete_missing_isNoOp() =
        runTest {
            val store = createStore()
            val target = store.fileOf(4L)
            assertFalse(target.exists())

            store.delete(4L)
            assertFalse(target.exists())
        }

    @Test
    fun PS5_write_createsMissingDirectories() =
        runTest {
            val subDir = File(tempFolder.root, "nested/path")
            val store = createStore(rootDir = subDir)
            val bytes = byteArrayOf(9, 8, 7)

            store.write(profileId = 5L, bytes = bytes)
            val target = store.fileOf(5L)
            assertTrue(target.exists())
            assertEquals(File(File(subDir, "avatars"), "5.jpg").absolutePath, target.absolutePath)
            assertArrayEquals(bytes, target.readBytes())
        }
}
