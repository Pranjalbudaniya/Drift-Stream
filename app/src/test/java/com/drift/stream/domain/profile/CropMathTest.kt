package com.drift.stream.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {
    private val floatTolerance = 1e-3f
    private val defaultBounds = CropBounds(imgW = 4000, imgH = 3000, window = 800)

    @Test
    fun CM1_minScale_landscape() {
        val scale = minScale(imgW = 4000, imgH = 3000, window = 800)
        assertEquals(800f / 3000f, scale, floatTolerance)
    }

    @Test
    fun CM2_minScale_portrait() {
        val scalePortrait = minScale(imgW = 3000, imgH = 4000, window = 800)
        assertEquals(800f / 3000f, scalePortrait, floatTolerance)

        val scaleTiny = minScale(imgW = 100, imgH = 100, window = 800)
        assertEquals(8.0f, scaleTiny, floatTolerance)
    }

    @Test
    fun CM3_clampOffset_whenImageEqualsWindow() {
        val initial = CropState(scale = 0.8f, offsetX = 50f, offsetY = -70f)
        val clamped = clampOffset(initial, imgW = 1000, imgH = 1000, window = 800)
        assertEquals(0f, clamped.offsetX, floatTolerance)
        assertEquals(0f, clamped.offsetY, floatTolerance)
    }

    @Test
    fun CM4_cropRect_initial_isCentredSquareOfShortSide() {
        val state = initialCropState(imgW = 4000, imgH = 3000, window = 800)
        val rect = cropRect(state, imgW = 4000, imgH = 3000, window = 800)
        assertEquals(500, rect.left)
        assertEquals(0, rect.top)
        assertEquals(3000, rect.size)
    }

    @Test
    fun CM5_cropRect_afterZoomTwo() {
        val state = CropState(scale = 0.53333336f, offsetX = 0f, offsetY = 0f)
        val rect = cropRect(state, imgW = 4000, imgH = 3000, window = 800)
        assertEquals(1250, rect.left)
        assertEquals(750, rect.top)
        assertEquals(1500, rect.size)
    }

    @Test
    fun CM6_cropRect_panFarRight_stopsAtImageEdge() {
        val state = CropState(scale = 0.53333336f, offsetX = 0f, offsetY = 0f)
        val panned =
            applyGesture(
                state = state,
                gesture = CropGesture(zoom = 1f, panX = 10000f, panY = 0f, focusX = 0f, focusY = 0f),
                bounds = defaultBounds,
            )
        assertEquals(666.6667f, panned.offsetX, 0.01f)
        val rect = cropRect(panned, imgW = 4000, imgH = 3000, window = 800)
        assertEquals(0, rect.left)
        assertEquals(750, rect.top)
    }

    @Test
    fun CM7_applyGesture_clampsZoomToRange() {
        val initial = initialCropState(imgW = 4000, imgH = 3000, window = 800)
        val min = minScale(4000, 3000, 800)
        val zoomedIn =
            applyGesture(
                state = initial,
                gesture = CropGesture(zoom = 100f, panX = 0f, panY = 0f, focusX = 0f, focusY = 0f),
                bounds = defaultBounds,
            )
        assertEquals(min * CROP_MAX_ZOOM, zoomedIn.scale, floatTolerance)

        val zoomedOut =
            applyGesture(
                state = zoomedIn,
                gesture = CropGesture(zoom = 0.01f, panX = 0f, panY = 0f, focusX = 0f, focusY = 0f),
                bounds = defaultBounds,
            )
        assertEquals(min, zoomedOut.scale, floatTolerance)
    }

    @Test
    fun CM8_applyGesture_zoomsAboutWindowCentre() {
        val state = CropState(scale = 0.53333336f, offsetX = 100f, offsetY = -40f)
        val updated =
            applyGesture(
                state = state,
                gesture = CropGesture(zoom = 1.5f, panX = 0f, panY = 0f, focusX = 0f, focusY = 0f),
                bounds = defaultBounds,
            )
        assertEquals(0.8f, updated.scale, floatTolerance)
        assertEquals(150f, updated.offsetX, 0.1f)
        assertEquals(-60f, updated.offsetY, 0.1f)
    }

    @Test
    fun CM9_initialCropState_isMinScaleAndZeroOffset() {
        val state = initialCropState(imgW = 4000, imgH = 3000, window = 800)
        assertEquals(800f / 3000f, state.scale, floatTolerance)
        assertEquals(0f, state.offsetX, floatTolerance)
        assertEquals(0f, state.offsetY, floatTolerance)
    }

    @Test
    fun CM10_cropRect_neverLeavesTheImage() {
        val imgW = 4000
        val imgH = 3000
        val window = 800
        val min = minScale(imgW, imgH, window)
        val testScales = listOf(min, 2f * min, 5f * min)
        val testPans = listOf(-100000f, 0f, 100000f)

        for (scale in testScales) {
            for (panX in testPans) {
                for (panY in testPans) {
                    val state =
                        applyGesture(
                            state = CropState(scale, 0f, 0f),
                            gesture = CropGesture(zoom = 1f, panX = panX, panY = panY, focusX = 0f, focusY = 0f),
                            bounds = defaultBounds,
                        )
                    val rect = cropRect(state, imgW, imgH, window)
                    assertTrue("left >= 0", rect.left >= 0)
                    assertTrue("top >= 0", rect.top >= 0)
                    assertTrue("left + size <= imgW", rect.left + rect.size <= imgW)
                    assertTrue("top + size <= imgH", rect.top + rect.size <= imgH)
                }
            }
        }
    }

    @Test
    fun CM11_applyGesture_zoomsAboutFocusPoint() {
        val state = CropState(scale = 0.53333336f, offsetX = 100f, offsetY = -40f)
        val updated =
            applyGesture(
                state = state,
                gesture = CropGesture(zoom = 1.5f, panX = 0f, panY = 0f, focusX = 200f, focusY = 0f),
                bounds = defaultBounds,
            )
        assertEquals(0.8f, updated.scale, floatTolerance)
        assertEquals(50f, updated.offsetX, floatTolerance)
        assertEquals(-60f, updated.offsetY, floatTolerance)
    }

    @Test
    fun CM12_applyGesture_focusAtCentre_equalsOldBehaviour() {
        val state = CropState(scale = 0.53333336f, offsetX = 100f, offsetY = -40f)
        val updated =
            applyGesture(
                state = state,
                gesture = CropGesture(zoom = 1.5f, panX = 0f, panY = 0f, focusX = 0f, focusY = 0f),
                bounds = defaultBounds,
            )
        assertEquals(0.8f, updated.scale, floatTolerance)
        assertEquals(150f, updated.offsetX, floatTolerance)
        assertEquals(-60f, updated.offsetY, floatTolerance)
    }

    @Test
    fun CM13_applyGesture_pointUnderFingersStaysPut() {
        val state = CropState(scale = 0.53333336f, offsetX = 0f, offsetY = 0f)
        val updated =
            applyGesture(
                state = state,
                gesture = CropGesture(zoom = 1.5f, panX = 0f, panY = 0f, focusX = 100f, focusY = 50f),
                bounds = defaultBounds,
            )
        assertEquals(-50f, updated.offsetX, floatTolerance)
        assertEquals(-25f, updated.offsetY, floatTolerance)
    }

    @Test
    fun CM14_rebase_keepsZoomFactorAndCentrePoint() {
        val oldBounds = CropBounds(imgW = 4000, imgH = 3000, window = 800)
        val newBounds = CropBounds(imgW = 4000, imgH = 3000, window = 400)
        val state = CropState(scale = 0.53333336f, offsetX = 100f, offsetY = 0f)
        val rebased = rebaseCropState(state, oldBounds, newBounds)
        assertEquals(0.26667f, rebased.scale, floatTolerance)
        assertEquals(50f, rebased.offsetX, floatTolerance)
        assertEquals(0f, rebased.offsetY, floatTolerance)
    }
}
