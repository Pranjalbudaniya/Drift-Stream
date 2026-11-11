package com.drift.stream.presentation.theme.tokens

import androidx.compose.ui.graphics.Color
import com.drift.stream.domain.profile.AVATAR_COLOR_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AvatarPaletteTest {
    @Test
    fun AV1_paletteHasTwelveEntries_matchingAvatarColorCount() {
        assertEquals(AVATAR_COLOR_COUNT, AvatarPalette.size)
        assertEquals(12, AvatarPalette.size)
    }

    @Test
    fun AV2_everyColourMeetsContrast4_5_withAvatarContent() {
        // WCAG relative luminance
        fun channelLuminance(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).pow(2.4)
            }
        }

        fun relativeLuminance(color: Color): Double {
            val r = channelLuminance(color.red)
            val g = channelLuminance(color.green)
            val b = channelLuminance(color.blue)
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        val whiteLuminance = relativeLuminance(AvatarContent) // 1.0

        for (color in AvatarPalette) {
            val lum = relativeLuminance(color)
            val contrast = (whiteLuminance + 0.05) / (lum + 0.05)
            assertTrue("Contrast for $color was $contrast, expected >= 4.5", contrast >= 4.5)
        }
    }

    @Test
    fun AV3_avatarColorOf_wrapsOutOfRangeIndexes() {
        assertEquals(AvatarPalette[0], avatarColorOf(12))
        assertEquals(AvatarPalette[11], avatarColorOf(-1))
        // Does not throw for Int extremes
        avatarColorOf(Int.MIN_VALUE)
        avatarColorOf(Int.MAX_VALUE)
    }

    @Test
    fun AV4_palette_hasNoDuplicateColours() {
        val uniqueColors = AvatarPalette.toSet()
        assertEquals(12, uniqueColors.size)
    }

    @Test
    fun AV5_emojiList_has40Distinct_singleCodePoint_entries() {
        assertEquals(40, AvatarEmojis.size)
        val unique = AvatarEmojis.toSet()
        assertEquals(40, unique.size)
        for (emoji in AvatarEmojis) {
            assertTrue("Emoji must not be blank", emoji.isNotBlank())
            assertEquals(
                "Emoji '$emoji' should be exactly one code point",
                1,
                emoji.codePointCount(0, emoji.length),
            )
        }
    }
}
