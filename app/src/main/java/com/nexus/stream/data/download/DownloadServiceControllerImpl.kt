package com.nexus.stream.data.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nexus.stream.domain.repository.DownloadServiceController
import com.nexus.stream.domain.usecase.SERVICE_MIN_GAP_MS
import com.nexus.stream.domain.usecase.shouldStartService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadServiceControllerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DownloadServiceController {
        private var lastStartAtMs: Long? = null

        override fun ensureRunning() {
            val now = System.currentTimeMillis()
            if (!shouldStartService(DownloadService.isRunning, lastStartAtMs, now, SERVICE_MIN_GAP_MS)) {
                return
            }
            lastStartAtMs = now
            val intent = Intent(context, DownloadService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Refused foreground service start is swallowed
            }
        }

        override fun stop() {
            // Documented no-op: service manages its own lifecycle and stops via shouldStopService
        }
    }
