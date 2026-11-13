package com.drift.stream.data.profile

import android.app.ActivityManager
import android.content.Context
import com.drift.stream.domain.profile.AppDataEraser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidAppDataEraser
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppDataEraser {
        override fun eraseEverything(): Boolean =
            try {
                val am = context.getSystemService(ActivityManager::class.java)
                am?.clearApplicationUserData() ?: false
            } catch (_: Exception) {
                false
            }
    }
