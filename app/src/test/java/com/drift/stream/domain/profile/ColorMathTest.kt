package com.drift.stream.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMathTest {
    private val floatTolerance = 0.01f

    @Test
    fun HC1_hsvToArgb_red() {
        val argb = hsvToArgb(0f, 1f, 1f)
        assertEquals(0xFFFF0000.toInt(), argb)
    }

    @Test
    fun HC2_hsvToArgb_green() {
        val argb = hsvToArgb(120f, 1f, 1f)
        assertEquals(0xFF00FF00.toInt(), argb)
    }

    @Test
    fun HC3_hsvToArgb_blue() {
        val argb = hsvToArgb(240f, 1f, 1f)
        assertEquals(0xFF0000FF.toInt(), argb)
    }

    @Test
    fun HC4_hsvToArgb_whiteAndBlack() {
        val white = hsvToArgb(0f, 0f, 1f)
        assertEquals(0xFFFFFFFF.toInt(), white)

        val black = hsvToArgb(0f, 0f, 0f)
        assertEquals(0xFF000000.toInt(), black)
    }

    @Test
    fun HC5_hsvToArgb_hue360_wrapsToRed() {
        val argb = hsvToArgb(360f, 1f, 1f)
        assertEquals(0xFFFF0000.toInt(), argb)
    }

    @Test
    fun HC6_hsvToArgb_yellow() {
        val argb = hsvToArgb(60f, 1f, 1f)
        assertEquals(0xFFFFFF00.toInt(), argb)
    }

    @Test
    fun HC7_argbToHsv_green() {
        val hsv = argbToHsv(0xFF00FF00.toInt())
        assertEquals(120f, hsv.hue, floatTolerance)
        assertEquals(1f, hsv.saturation, floatTolerance)
        assertEquals(1f, hsv.value, floatTolerance)
    }

    @Test
    fun HC8_argbToHsv_grey() {
        val hsv = argbToHsv(0xFF808080.toInt())
        assertEquals(0f, hsv.saturation, floatTolerance)
        assertEquals(0.502f, hsv.value, floatTolerance)
    }
}
