package com.drift.stream.presentation.feature.streams

import com.drift.stream.domain.model.TypeFilter

object StreamFilterMemory {
    var resolution: Int? = null
    var language: String? = null
    var type: TypeFilter = TypeFilter.ALL
    var cachedOnly: Boolean = false

    fun reset() {
        resolution = null
        language = null
        type = TypeFilter.ALL
        cachedOnly = false
    }
}
