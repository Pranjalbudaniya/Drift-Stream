package com.drift.stream.presentation.theme.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.domain.profile.AVATAR_COLOR_COUNT
import com.drift.stream.domain.profile.normalizeAvatarColor

val AvatarContent: Color = Color.White

val AvatarPalette: List<Color> =
    listOf(
        Color(0xFFC62828), // 0: Red
        Color(0xFFBF360C), // 1: Orange
        Color(0xFF8D6E00), // 2: Amber
        Color(0xFF5F7A00), // 3: Lime
        Color(0xFF2E7D32), // 4: Green
        Color(0xFF00796B), // 5: Teal
        Color(0xFF6D4C41), // 6: Brown
        Color(0xFF1565C0), // 7: Blue
        Color(0xFF5C6BC0), // 8: Indigo
        Color(0xFF8E24AA), // 9: Purple
        Color(0xFFC2185B), // 10: Pink
        Color(0xFF546E7A), // 11: Slate
    )

val AvatarColors: List<Color> = AvatarPalette

val AvatarColorNames: List<Int> =
    listOf(
        R.string.avatar_color_0,
        R.string.avatar_color_1,
        R.string.avatar_color_2,
        R.string.avatar_color_3,
        R.string.avatar_color_4,
        R.string.avatar_color_5,
        R.string.avatar_color_6,
        R.string.avatar_color_7,
        R.string.avatar_color_8,
        R.string.avatar_color_9,
        R.string.avatar_color_10,
        R.string.avatar_color_11,
    )

val AvatarEmojis: List<String> =
    listOf(
        "😀",
        "😎",
        "🤓",
        "🥳",
        "😴",
        "🦊",
        "🐱",
        "🐶",
        "🐼",
        "🦁",
        "🐸",
        "🐵",
        "🦄",
        "🐙",
        "🐧",
        "🦉",
        "🐢",
        "🦋",
        "🚀",
        "⭐",
        "🎬",
        "🍿",
        "🎮",
        "🎧",
        "⚽",
        "🏀",
        "🌈",
        "🔥",
        "🌙",
        "🍕",
        "🌸",
        "🌻",
        "🍀",
        "🎸",
        "🎨",
        "📚",
        "🧸",
        "👑",
        "💎",
        "🛸",
    )

val AvatarPreviewSize = 96.dp
val AvatarTileSize = 96.dp
val AvatarTouchTargetSize = 48.dp
val AvatarSwatchCircleSize = 40.dp
const val AVATAR_TEXT_RATIO = 0.5f
val AvatarTextRatio: Float get() = AVATAR_TEXT_RATIO

fun avatarColorOf(index: Int): Color = AvatarPalette[normalizeAvatarColor(index)]

fun avatarColorNameRes(index: Int): Int = AvatarColorNames[normalizeAvatarColor(index)]
