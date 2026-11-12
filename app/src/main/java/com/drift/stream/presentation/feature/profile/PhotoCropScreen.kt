package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.domain.profile.CropBounds
import com.drift.stream.domain.profile.CropGesture
import com.drift.stream.domain.profile.CropState
import com.drift.stream.domain.profile.applyGesture
import com.drift.stream.domain.profile.displayRect
import com.drift.stream.domain.profile.initialCropState
import com.drift.stream.domain.profile.rebaseCropState
import com.drift.stream.presentation.common.NexusIconButton
import com.drift.stream.presentation.theme.NexusTheme
import kotlin.math.min
import kotlin.math.roundToInt

private val TOP_BAR_HEIGHT = 56.dp
private val CROP_WINDOW_MARGIN = 32.dp
private val CIRCLE_STROKE_WIDTH = 2.dp
private val HINT_BOTTOM_PADDING = 32.dp
private const val MIN_WINDOW_SIZE = 100
private const val DIM_ALPHA = 0.75f
private const val OUTLINE_ALPHA = 0.85f

@Composable
fun PhotoCropScreen(
    bitmap: Bitmap,
    onDone: (CropState, Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCancel)

    val density = LocalDensity.current
    val marginPx = remember(density) { with(density) { CROP_WINDOW_MARGIN.roundToPx() } }

    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var isDoneClicked by rememberSaveable { mutableStateOf(false) }

    val bounds =
        remember(bitmap.width, bitmap.height, areaSize, marginPx) {
            computeCropBounds(bitmap.width, bitmap.height, areaSize, marginPx)
        }

    var savedScale by rememberSaveable { mutableFloatStateOf(0f) }
    var savedOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var savedOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var previousBounds by remember { mutableStateOf<CropBounds?>(null) }

    if (bounds != null) {
        val resolved = resolveCropState(bounds, previousBounds, CropState(savedScale, savedOffsetX, savedOffsetY))
        savedScale = resolved.scale
        savedOffsetX = resolved.offsetX
        savedOffsetY = resolved.offsetY
        previousBounds = bounds
    }

    val cropState = CropState(savedScale, savedOffsetX, savedOffsetY)
    val currentCropState by rememberUpdatedState(cropState)

    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val dimColor = MaterialTheme.colorScheme.scrim.copy(alpha = DIM_ALPHA)
    val outlineColor = MaterialTheme.colorScheme.surface.copy(alpha = OUTLINE_ALPHA)

    val isDoneEnabled = bounds != null && !isDoneClicked

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        CropTopBar(
            onCancel = onCancel,
            onDone = {
                val currentBounds = bounds
                if (!isDoneClicked && currentBounds != null) {
                    isDoneClicked = true
                    onDone(cropState, currentBounds.window)
                }
            },
            isDoneEnabled = isDoneEnabled,
        )

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { areaSize = it }
                    .pointerInput(bounds) {
                        if (bounds != null) {
                            detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                                val areaCentre = Offset(size.width / 2f, size.height / 2f)
                                val focus = centroid - areaCentre
                                val next =
                                    applyGesture(
                                        state = currentCropState,
                                        gesture =
                                            CropGesture(
                                                zoom = zoom,
                                                panX = pan.x,
                                                panY = pan.y,
                                                focusX = focus.x,
                                                focusY = focus.y,
                                            ),
                                        bounds = bounds,
                                    )
                                savedScale = next.scale
                                savedOffsetX = next.offsetX
                                savedOffsetY = next.offsetY
                            }
                        }
                    },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (bounds != null) {
                    drawCropContent(image, bounds, cropState, dimColor, outlineColor)
                }
            }

            Text(
                text = stringResource(R.string.photo_crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = HINT_BOTTOM_PADDING)
                        .fillMaxWidth(),
            )
        }
    }
}

private fun computeCropBounds(
    imgW: Int,
    imgH: Int,
    areaSize: IntSize,
    marginPx: Int,
): CropBounds? {
    val hasValidSize = areaSize.width > 0 && areaSize.height > 0
    val hasValidImage = imgW > 0 && imgH > 0
    if (!hasValidSize || !hasValidImage) {
        return null
    }
    val window = (min(areaSize.width, areaSize.height) - 2 * marginPx).coerceAtLeast(MIN_WINDOW_SIZE)
    return CropBounds(imgW = imgW, imgH = imgH, window = window)
}

private fun resolveCropState(
    bounds: CropBounds,
    previousBounds: CropBounds?,
    current: CropState,
): CropState =
    when {
        current.scale <= 0f -> initialCropState(bounds.imgW, bounds.imgH, bounds.window)
        previousBounds != null && previousBounds != bounds -> rebaseCropState(current, previousBounds, bounds)
        else -> current
    }

@Composable
private fun CropTopBar(
    onCancel: () -> Unit,
    onDone: () -> Unit,
    isDoneEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(TOP_BAR_HEIGHT)
                .padding(horizontal = NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NexusIconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.avatar_action_cancel),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.width(NexusTheme.spacing.sm))

        Text(
            text = stringResource(R.string.photo_crop_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.weight(1f))

        NexusIconButton(
            onClick = onDone,
            enabled = isDoneEnabled,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.avatar_action_done),
                tint =
                    if (isDoneEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }
    }
}

private fun DrawScope.drawCropContent(
    image: ImageBitmap,
    bounds: CropBounds,
    cropState: CropState,
    dimColor: Color,
    outlineColor: Color,
) {
    if (cropState.scale <= 0f) return
    val rect = displayRect(cropState, bounds.imgW, bounds.imgH)
    val centreX = size.width / 2f
    val centreY = size.height / 2f

    val dstLeft = (centreX + rect.left).roundToInt()
    val dstTop = (centreY + rect.top).roundToInt()
    val dstWidth = rect.width.roundToInt()
    val dstHeight = rect.height.roundToInt()

    drawImage(
        image = image,
        dstOffset = IntOffset(dstLeft, dstTop),
        dstSize = IntSize(dstWidth, dstHeight),
    )

    val radius = bounds.window.toFloat() / 2f
    val circlePath =
        Path().apply {
            addOval(Rect(center = Offset(centreX, centreY), radius = radius))
        }

    clipPath(circlePath, clipOp = ClipOp.Difference) {
        drawRect(color = dimColor)
    }

    drawCircle(
        color = outlineColor,
        radius = radius,
        center = Offset(centreX, centreY),
        style = Stroke(width = CIRCLE_STROKE_WIDTH.toPx()),
    )
}

@Preview(name = "Crop Screen - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun PhotoCropScreenDarkPreview() {
    NexusTheme {
        val previewBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        PhotoCropScreen(
            bitmap = previewBitmap,
            onDone = { _, _ -> },
            onCancel = {},
        )
    }
}

@Preview(name = "Crop Screen - Light", showBackground = true)
@Composable
internal fun PhotoCropScreenLightPreview() {
    NexusTheme {
        val previewBitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        PhotoCropScreen(
            bitmap = previewBitmap,
            onDone = { _, _ -> },
            onCancel = {},
        )
    }
}
