package com.drift.stream.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.profile.AVATAR_DECODE_MAX_SIDE
import com.drift.stream.domain.profile.AVATAR_PHOTO_JPEG_QUALITY
import com.drift.stream.domain.profile.AVATAR_PHOTO_SIZE_PX
import com.drift.stream.domain.profile.CropRect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

interface AvatarPhotoPipeline {
    suspend fun decode(uri: Uri): Bitmap?

    suspend fun cropAndSavePending(
        bitmap: Bitmap,
        cropRect: CropRect,
    ): String?

    suspend fun readBytes(path: String): ByteArray?

    fun deletePending(path: String?)
}

@Singleton
class AvatarPhotoPipelineImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : AvatarPhotoPipeline {
        override suspend fun decode(uri: Uri): Bitmap? =
            withContext(ioDispatcher) {
                try {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val size = info.size
                        val maxSide = maxOf(size.width, size.height)
                        if (maxSide > AVATAR_DECODE_MAX_SIDE) {
                            val scale = AVATAR_DECODE_MAX_SIDE.toFloat() / maxSide
                            val targetW = (size.width * scale).roundToInt().coerceAtLeast(1)
                            val targetH = (size.height * scale).roundToInt().coerceAtLeast(1)
                            decoder.setTargetSize(targetW, targetH)
                        }
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }

        override suspend fun cropAndSavePending(
            bitmap: Bitmap,
            cropRect: CropRect,
        ): String? =
            withContext(ioDispatcher) {
                try {
                    val cropped =
                        Bitmap.createBitmap(
                            bitmap,
                            cropRect.left,
                            cropRect.top,
                            cropRect.size,
                            cropRect.size,
                        )
                    val scaled =
                        if (cropped.width != AVATAR_PHOTO_SIZE_PX || cropped.height != AVATAR_PHOTO_SIZE_PX) {
                            Bitmap.createScaledBitmap(cropped, AVATAR_PHOTO_SIZE_PX, AVATAR_PHOTO_SIZE_PX, true)
                        } else {
                            cropped
                        }

                    val pendingDir = File(context.cacheDir, PENDING_DIR_NAME).apply { mkdirs() }
                    val token = UUID.randomUUID().toString()
                    val pendingFile = File(pendingDir, "$token$AVATAR_PENDING_EXTENSION")
                    FileOutputStream(pendingFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_PHOTO_JPEG_QUALITY, out)
                    }

                    pendingFile.absolutePath
                } catch (_: IOException) {
                    null
                } catch (_: OutOfMemoryError) {
                    null
                }
            }

        override suspend fun readBytes(path: String): ByteArray? =
            withContext(ioDispatcher) {
                try {
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        file.readBytes()
                    } else {
                        null
                    }
                } catch (_: IOException) {
                    null
                }
            }

        override fun deletePending(path: String?) {
            if (path == null) return
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: IOException) {
                // best effort
            } catch (_: SecurityException) {
                // best effort
            }
        }

        companion object {
            private const val PENDING_DIR_NAME = "avatar_pending"
            private const val AVATAR_PENDING_EXTENSION = ".jpg"
        }
    }
