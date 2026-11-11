package com.drift.stream.domain.profile

const val AVATAR_COLOR_COUNT = 12
const val PROFILE_NAME_MAX_LENGTH = 20

sealed interface NameCheck {
    data class Valid(
        val name: String,
    ) : NameCheck

    data object Empty : NameCheck

    data object TooLong : NameCheck

    data object Invalid : NameCheck

    data object Duplicate : NameCheck
}

fun normalizeAvatarColor(i: Int): Int = Math.floorMod(i, AVATAR_COLOR_COUNT)

fun validateProfileName(
    raw: String,
    otherNames: Collection<String>,
): NameCheck {
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() || trimmed.all { it.isWhitespace() || Character.isSpaceChar(it) } -> {
            NameCheck.Empty
        }

        hasIsoControl(trimmed) -> {
            NameCheck.Invalid
        }

        trimmed.codePointCount(0, trimmed.length) > PROFILE_NAME_MAX_LENGTH -> {
            NameCheck.TooLong
        }

        otherNames.any { it.trim().equals(trimmed, ignoreCase = true) } -> {
            NameCheck.Duplicate
        }

        else -> {
            NameCheck.Valid(trimmed)
        }
    }
}

private fun hasIsoControl(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (Character.isISOControl(cp)) return true
        i += Character.charCount(cp)
    }
    return false
}

fun avatarInitial(name: String): String {
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        if (Character.isLetterOrDigit(cp)) {
            val upperCp = Character.toUpperCase(cp)
            return String(Character.toChars(upperCp))
        }
        i += Character.charCount(cp)
    }
    return "?"
}

fun nextFreeAvatarColor(used: Collection<Int>): Int {
    val counts = IntArray(AVATAR_COLOR_COUNT)
    for (color in used) {
        counts[normalizeAvatarColor(color)]++
    }

    for (i in 0 until AVATAR_COLOR_COUNT) {
        if (counts[i] == 0) return i
    }

    var minCount = Int.MAX_VALUE
    var minIndex = 0
    for (i in 0 until AVATAR_COLOR_COUNT) {
        if (counts[i] < minCount) {
            minCount = counts[i]
            minIndex = i
        }
    }
    return minIndex
}
