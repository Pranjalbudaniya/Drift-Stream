package com.drift.stream.domain.usecase

sealed interface NameResult {
    data class Valid(
        val trimmedName: String,
    ) : NameResult

    data object Blank : NameResult

    data object TooLong : NameResult

    data object Duplicate : NameResult
}

object ProfileNameRules {
    private const val MAX_NAME_LENGTH = 20

    fun validate(
        name: String,
        existingNames: List<String>,
    ): NameResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameResult.Blank
            trimmed.length > MAX_NAME_LENGTH -> NameResult.TooLong
            existingNames.any { it.equals(trimmed, ignoreCase = true) } -> NameResult.Duplicate
            else -> NameResult.Valid(trimmed)
        }
    }
}
