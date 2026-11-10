package com.drift.stream.domain.model

enum class AddonSource {
    LOCAL,
    STREMIO_ACCOUNT,
}

sealed interface AddonOwner {
    data object Own : AddonOwner

    data class Other(
        val profileId: Long,
        val name: String,
    ) : AddonOwner
}

data class Addon(
    val transportUrl: String,
    val manifest: AddonManifest,
    val enabled: Boolean,
    val position: Int,
    val source: AddonSource,
    val installedAt: Long,
    val shared: Boolean = false,
    val owner: AddonOwner = AddonOwner.Own,
) {
    val isAdult: Boolean get() = manifest.isAdult
}
