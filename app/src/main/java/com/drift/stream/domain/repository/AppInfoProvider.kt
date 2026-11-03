package com.drift.stream.domain.repository

interface AppInfoProvider {
    val versionName: String?
    val versionCode: Long
}
