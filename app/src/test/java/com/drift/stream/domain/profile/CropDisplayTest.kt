package com.drift.stream.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropDisplayTest {
    private val floatTolerance = 1e-3f

    @Test
    fun DR1_displayRect_initial_coversWindow() {
        val state = initialCropState(imgW = 4000, imgH = 3000, window = 800)
        val rect = displayRect(state, imgW = 4000, imgH = 3000)
        assertEquals(-533.333f, rect.left, floatTolerance)
        assertEquals(-400f, rect.top, floatTolerance)
        assertEquals(1066.667f, rect.width, floatTolerance)
        assertEquals(800f, rect.height, floatTolerance)
    }

    @Test
    fun DR2_displayRect_followsOffset() {
        val state = CropState(scale = 0.8f, offsetX = 150f, offsetY = -60f)
        val rect = displayRect(state, imgW = 4000, imgH = 3000)
        assertEquals(-1450f, rect.left, floatTolerance)
        assertEquals(-1260f, rect.top, floatTolerance)
        assertEquals(3200f, rect.width, floatTolerance)
        assertEquals(2400f, rect.height, floatTolerance)
    }

    @Test
    fun DR3_displayRect_alwaysCoversWindow() {
        val images =
            listOf(
                Pair(4000, 3000),
                Pair(3000, 4000),
                Pair(1000, 1000),
                Pair(2048, 1536),
            )
        val window = 800
        val halfWindow = 400f
        val boundEpsilon = 0.01f

        // 40 deterministic gestures with zoom between 0.8 and 1.3, pans of +-300, focus points around window
        val gestures =
            (0 until 40).map { i ->
                val zoom = 0.8f + (i % 6) * 0.1f
                val panX = -300f + ((i * 37) % 601).toFloat()
                val panY = -300f + ((i * 43) % 601).toFloat()
                val focusX = -380f + ((i * 53) % 761).toFloat()
                val focusY = -380f + ((i * 59) % 761).toFloat()
                CropGesture(zoom = zoom, panX = panX, panY = panY, focusX = focusX, focusY = focusY)
            }

        for ((imgW, imgH) in images) {
            val bounds = CropBounds(imgW = imgW, imgH = imgH, window = window)
            var state = initialCropState(imgW, imgH, window)

            for (gesture in gestures) {
                state = applyGesture(state, gesture, bounds)
                val rect = displayRect(state, imgW, imgH)

                assertTrue("left <= -400 + 0.01 for ($imgW, $imgH)", rect.left <= -halfWindow + boundEpsilon)
                assertTrue("top <= -400 + 0.01 for ($imgW, $imgH)", rect.top <= -halfWindow + boundEpsilon)
                assertTrue(
                    "left + width >= 400 - 0.01 for ($imgW, $imgH)",
                    rect.left + rect.width >= halfWindow - boundEpsilon,
                )
                assertTrue(
                    "top + height >= 400 - 0.01 for ($imgW, $imgH)",
                    rect.top + rect.height >= halfWindow - boundEpsilon,
                )
            }
        }
    }

    @Test
    fun DR4_displayRect_agreesWithCropRect() {
        val imgW = 4000
        val imgH = 3000
        val window = 800
        val bounds = CropBounds(imgW, imgH, window)
        val min = minScale(imgW, imgH, window)

        val states =
            listOf(
                initialCropState(imgW, imgH, window),
                CropState(scale = min * 2f, offsetX = 0f, offsetY = 0f),
                CropState(scale = min * 5f, offsetX = 0f, offsetY = 0f),
                applyGesture(
                    CropState(scale = min * 2f, offsetX = 0f, offsetY = 0f),
                    CropGesture(zoom = 1f, panX = -5000f, panY = 0f),
                    bounds,
                ),
                applyGesture(
                    CropState(scale = min * 2f, offsetX = 0f, offsetY = 0f),
                    CropGesture(zoom = 1f, panX = 0f, panY = -5000f),
                    bounds,
                ),
            )

        for (state in states) {
            val dRect = displayRect(state, imgW, imgH)
            val cRect = cropRect(state, imgW, imgH, window)

            val leftMapped = (-400f - dRect.left) / state.scale
            val topMapped = (-400f - dRect.top) / state.scale
            val rightMapped = (400f - dRect.left) / state.scale
            val bottomMapped = (400f - dRect.top) / state.scale

            assertEquals("left matches within +-1", cRect.left.toFloat(), leftMapped, 1.0f)
            assertEquals("top matches within +-1", cRect.top.toFloat(), topMapped, 1.0f)
            assertEquals("right matches within +-1", (cRect.left + cRect.size).toFloat(), rightMapped, 1.0f)
            assertEquals("bottom matches within +-1", (cRect.top + cRect.size).toFloat(), bottomMapped, 1.0f)
        }
    }
}
