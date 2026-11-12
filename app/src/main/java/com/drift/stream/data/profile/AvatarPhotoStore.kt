package com.drift.stream.data.profile

import android.content.Context
import com.drift.stream.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

interface AvatarPhotoStore {
    fun fileOf(profileId: Long): File

    suspend fun write(
        profileId: Long,
        bytes: ByteArray,
    )

    suspend fun delete(profileId: Long)
}

@Singleton
class AvatarPhotoStoreImpl(
    private val rootDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AvatarPhotoStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(context.filesDir, ioDispatcher)

    private val avatarsDir: File
        get() = File(rootDir, AVATARS_DIR_NAME)

    override fun fileOf(profileId: Long): File = avatarPhotoFile(rootDir, profileId)

    override suspend fun write(
        profileId: Long,
        bytes: ByteArray,
    ) = withContext(ioDispatcher) {
        val dir = avatarsDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val target = fileOf(profileId)
        val tempFile = File.createTempFile("avatar_${profileId}_", ".tmp", dir)
        try {
            FileOutputStream(tempFile).use { output ->
                output.write(bytes)
                output.flush()
            }
            moveFile(tempFile, target)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    override suspend fun delete(profileId: Long) =
        withContext(ioDispatcher) {
            val file = fileOf(profileId)
            if (file.exists()) {
                file.delete()
            }
            Unit
        }

    private fun moveFile(
        source: File,
        target: File,
    ) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (ignored: IOException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    companion object {
        private const val AVATARS_DIR_NAME = "avatars"
    }
}
