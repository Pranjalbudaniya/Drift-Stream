package com.drift.stream.domain.profile

import kotlin.math.max
import kotlin.math.roundToInt

const val CROP_MAX_ZOOM = 5f
const val AVATAR_PHOTO_SIZE_PX = 512
const val AVATAR_PHOTO_JPEG_QUALITY = 90
const val AVATAR_DECODE_MAX_SIDE = 2048

data class CropState(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

data class CropRect(
    val left: Int,
    val top: Int,
    val size: Int,
)

data class CropBounds(
    val imgW: Int,
    val imgH: Int,
    val window: Int,
)

data class CropGesture(
    val zoom: Float,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val focusX: Float = 0f,
    val focusY: Float = 0f,
)

data class DisplayRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

fun minScale(
    imgW: Int,
    imgH: Int,
    window: Int,
): Float = max(window.toFloat() / imgW.toFloat(), window.toFloat() / imgH.toFloat())

fun initialCropState(
    imgW: Int,
    imgH: Int,
    window: Int,
): CropState =
    CropState(
        scale = minScale(imgW, imgH, window),
        offsetX = 0f,
        offsetY = 0f,
    )

fun clampScale(
    scale: Float,
    minScale: Float,
): Float = scale.coerceIn(minScale, minScale * CROP_MAX_ZOOM)

fun clampOffset(
    state: CropState,
    imgW: Int,
    imgH: Int,
    window: Int,
): CropState {
    val mx = max(0f, (imgW.toFloat() * state.scale - window.toFloat()) / 2f)
    val my = max(0f, (imgH.toFloat() * state.scale - window.toFloat()) / 2f)
    return state.copy(
        offsetX = state.offsetX.coerceIn(-mx, mx),
        offsetY = state.offsetY.coerceIn(-my, my),
    )
}

fun applyGesture(
    state: CropState,
    gesture: CropGesture,
    bounds: CropBounds,
): CropState {
    val min = minScale(bounds.imgW, bounds.imgH, bounds.window)
    val newScale = clampScale(state.scale * gesture.zoom, min)
    val ratio = newScale / state.scale
    val newOffsetX = gesture.focusX * (1f - ratio) + state.offsetX * ratio + gesture.panX
    val newOffsetY = gesture.focusY * (1f - ratio) + state.offsetY * ratio + gesture.panY
    return clampOffset(
        CropState(newScale, newOffsetX, newOffsetY),
        imgW = bounds.imgW,
        imgH = bounds.imgH,
        window = bounds.window,
    )
}

fun displayRect(
    state: CropState,
    imgW: Int,
    imgH: Int,
): DisplayRect {
    val width = imgW.toFloat() * state.scale
    val height = imgH.toFloat() * state.scale
    val left = state.offsetX - width / 2f
    val top = state.offsetY - height / 2f
    return DisplayRect(left = left, top = top, width = width, height = height)
}

fun rebaseCropState(
    old: CropState,
    oldBounds: CropBounds,
    newBounds: CropBounds,
): CropState {
    val oldMin = minScale(oldBounds.imgW, oldBounds.imgH, oldBounds.window)
    val newMin = minScale(newBounds.imgW, newBounds.imgH, newBounds.window)
    val zoomFactor = old.scale / oldMin
    val newScale = clampScale(newMin * zoomFactor, newMin)
    val cx = oldBounds.imgW.toFloat() / 2f - old.offsetX / old.scale
    val cy = oldBounds.imgH.toFloat() / 2f - old.offsetY / old.scale
    val newOffsetX = (newBounds.imgW.toFloat() / 2f - cx) * newScale
    val newOffsetY = (newBounds.imgH.toFloat() / 2f - cy) * newScale
    return clampOffset(
        CropState(newScale, newOffsetX, newOffsetY),
        imgW = newBounds.imgW,
        imgH = newBounds.imgH,
        window = newBounds.window,
    )
}

fun cropRect(
    state: CropState,
    imgW: Int,
    imgH: Int,
    window: Int,
): CropRect {
    val sizeFloat = window.toFloat() / state.scale
    val size = sizeFloat.roundToInt().coerceIn(1, minOf(imgW, imgH))
    val cx = imgW.toFloat() / 2f - state.offsetX / state.scale
    val cy = imgH.toFloat() / 2f - state.offsetY / state.scale
    val maxLeft = max(0, imgW - size)
    val maxTop = max(0, imgH - size)
    val left = (cx - sizeFloat / 2f).roundToInt().coerceIn(0, maxLeft)
    val top = (cy - sizeFloat / 2f).roundToInt().coerceIn(0, maxTop)
    return CropRect(left = left, top = top, size = size)
}
