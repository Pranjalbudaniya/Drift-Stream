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
    zoom: Float,
    panX: Float,
    panY: Float,
    bounds: CropBounds,
): CropState {
    val min = minScale(bounds.imgW, bounds.imgH, bounds.window)
    val newScale = clampScale(state.scale * zoom, min)
    val ratio = newScale / state.scale
    val newOffsetX = state.offsetX * ratio + panX
    val newOffsetY = state.offsetY * ratio + panY
    return clampOffset(
        CropState(newScale, newOffsetX, newOffsetY),
        imgW = bounds.imgW,
        imgH = bounds.imgH,
        window = bounds.window,
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
