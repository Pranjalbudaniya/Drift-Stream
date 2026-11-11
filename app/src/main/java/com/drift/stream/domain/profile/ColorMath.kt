package com.drift.stream.domain.profile

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

object ColorMath

data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int,
)

const val COLOR_WHITE_ARGB = 0xFFFFFFFF.toInt()
const val COLOR_BLACK_ARGB = 0xFF000000.toInt()

private const val HUE_MAX = 360f
private const val HUE_SECTOR = 60f
private const val HUE_SECTOR_1 = 60f
private const val HUE_SECTOR_2 = 120f
private const val HUE_SECTOR_3 = 180f
private const val HUE_SECTOR_4 = 240f
private const val HUE_SECTOR_5 = 300f
private const val BYTE_MASK = 0xFF
private const val BYTE_MAX_FLOAT = 255f
private const val BYTE_MAX_INT = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

private const val LUMINANCE_R_WEIGHT = 0.2126f
private const val LUMINANCE_G_WEIGHT = 0.7152f
private const val LUMINANCE_B_WEIGHT = 0.0722f
private const val LINEAR_THRESHOLD = 0.03928f
private const val LINEAR_DIVISOR = 12.92f
private const val GAMMA_OFFSET = 0.055f
private const val GAMMA_DIVISOR = 1.055f
private const val GAMMA_EXPONENT = 2.4f
private const val WCAG_OFFSET = 0.05f
private const val MAX_EMOJI_LENGTH = 32

fun forceOpaque(argb: Int): Int = argb or COLOR_BLACK_ARGB

fun hsvToArgb(
    h: Float,
    s: Float,
    v: Float,
): Int {
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val hue = (h % HUE_MAX + HUE_MAX) % HUE_MAX
    val c = value * sat
    val x = c * (1f - abs((hue / HUE_SECTOR) % 2f - 1f))
    val m = value - c

    val (rPrime, gPrime, bPrime) =
        when {
            hue < HUE_SECTOR_1 -> Triple(c, x, 0f)
            hue < HUE_SECTOR_2 -> Triple(x, c, 0f)
            hue < HUE_SECTOR_3 -> Triple(0f, c, x)
            hue < HUE_SECTOR_4 -> Triple(0f, x, c)
            hue < HUE_SECTOR_5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

    val r = ((rPrime + m) * BYTE_MAX_FLOAT).roundToInt().coerceIn(0, BYTE_MAX_INT)
    val g = ((gPrime + m) * BYTE_MAX_FLOAT).roundToInt().coerceIn(0, BYTE_MAX_INT)
    val b = ((bPrime + m) * BYTE_MAX_FLOAT).roundToInt().coerceIn(0, BYTE_MAX_INT)
    return COLOR_BLACK_ARGB or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
}

fun argbToHsv(argb: Int): HsvColor {
    val r = ((argb shr RED_SHIFT) and BYTE_MASK) / BYTE_MAX_FLOAT
    val g = ((argb shr GREEN_SHIFT) and BYTE_MASK) / BYTE_MAX_FLOAT
    val b = (argb and BYTE_MASK) / BYTE_MAX_FLOAT

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val v = max
    val s = if (max == 0f) 0f else delta / max
    var h =
        when {
            delta == 0f -> 0f
            max == r -> HUE_SECTOR * (((g - b) / delta) % 6f)
            max == g -> HUE_SECTOR * (((b - r) / delta) + 2f)
            else -> HUE_SECTOR * (((r - g) / delta) + 4f)
        }
    if (h < 0f) h += HUE_MAX
    return HsvColor(hue = h, saturation = s, value = v)
}

private fun linearizeChannel(channel: Float): Float =
    if (channel <= LINEAR_THRESHOLD) {
        channel / LINEAR_DIVISOR
    } else {
        ((channel + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA_EXPONENT)
    }

private fun relativeLuminance(argb: Int): Float {
    val r = linearizeChannel(((argb shr RED_SHIFT) and BYTE_MASK) / BYTE_MAX_FLOAT)
    val g = linearizeChannel(((argb shr GREEN_SHIFT) and BYTE_MASK) / BYTE_MAX_FLOAT)
    val b = linearizeChannel((argb and BYTE_MASK) / BYTE_MAX_FLOAT)
    return LUMINANCE_R_WEIGHT * r + LUMINANCE_G_WEIGHT * g + LUMINANCE_B_WEIGHT * b
}

fun contentColorFor(argb: Int): Int {
    val luminance = relativeLuminance(argb)
    val contrastWhite = (1.0f + WCAG_OFFSET) / (luminance + WCAG_OFFSET)
    val contrastBlack = (luminance + WCAG_OFFSET) / WCAG_OFFSET
    return if (contrastWhite > contrastBlack) COLOR_WHITE_ARGB else COLOR_BLACK_ARGB
}

fun isValidAvatarEmoji(emoji: String): Boolean {
    val lengthOk = emoji.isNotBlank() && emoji.length <= MAX_EMOJI_LENGTH
    return lengthOk && emoji.none { Character.isISOControl(it) }
}
