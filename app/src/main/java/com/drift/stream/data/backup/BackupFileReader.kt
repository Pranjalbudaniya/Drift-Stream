package com.drift.stream.data.backup

import android.content.Context
import android.net.Uri
import com.drift.stream.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FileReadResult {
    data class Success(
        val bytes: ByteArray,
    ) : FileReadResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data object TooLarge : FileReadResult

    data class Error(
        val cause: Throwable,
    ) : FileReadResult
}

private const val BUFFER_SIZE = 8192

@Singleton
open class BackupFileReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val contentResolver get() = context.contentResolver

        open suspend fun read(
            uri: Uri,
            maxBytes: Long,
        ): FileReadResult =
            withContext(ioDispatcher) {
                try {
                    val stream = contentResolver.openInputStream(uri)
                    if (stream != null) {
                        stream.use { readStream(it, maxBytes) }
                    } else {
                        FileReadResult.Error(IllegalArgumentException("Cannot open stream for $uri"))
                    }
                } catch (e: SecurityException) {
                    FileReadResult.Error(e)
                } catch (e: IOException) {
                    FileReadResult.Error(e)
                } catch (e: IllegalStateException) {
                    FileReadResult.Error(e)
                } catch (e: IllegalArgumentException) {
                    FileReadResult.Error(e)
                }
            }

        private fun readStream(
            input: InputStream,
            maxBytes: Long,
        ): FileReadResult {
            val buffer = ByteArray(BUFFER_SIZE)
            val output = ByteArrayOutputStream()
            var totalBytesRead = 0L

            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                totalBytesRead += bytesRead
                if (totalBytesRead > maxBytes) {
                    return FileReadResult.TooLarge
                }
                output.write(buffer, 0, bytesRead)
            }
            return FileReadResult.Success(output.toByteArray())
        }
    }
