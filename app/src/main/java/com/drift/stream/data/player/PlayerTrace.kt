package com.drift.stream.data.player

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log

class PlayerTrace(
    val enabled: () -> Boolean,
    val clock: () -> Long = { SystemClock.elapsedRealtime() },
    val sink: (String) -> Unit = { Log.d("DriftTrace", it) },
) {
    fun log(
        event: String,
        vararg fields: Pair<String, Any?>,
    ) {
        if (!enabled()) return
        val builder = StringBuilder("t=").append(clock()).append(' ').append(event)
        for ((key, value) in fields) {
            val sanitized = if (value is String && value.contains("://")) "<url>" else value
            builder
                .append(' ')
                .append(key)
                .append('=')
                .append(sanitized)
        }
        sink(builder.toString())
    }

    companion object {
        @Volatile
        private var isDebuggable: Boolean = false

        val default: PlayerTrace =
            PlayerTrace(
                enabled = { isDebuggable },
                clock = { SystemClock.elapsedRealtime() },
                sink = { Log.d("DriftTrace", it) },
            )

        fun init(context: Context) {
            isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }

        fun log(
            event: String,
            vararg fields: Pair<String, Any?>,
        ) {
            default.log(event, *fields)
        }
    }
}
