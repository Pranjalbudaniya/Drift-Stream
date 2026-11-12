package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.domain.profile.CropBounds
import com.drift.stream.domain.profile.CropState
import com.drift.stream.domain.profile.applyGesture
import com.drift.stream.domain.profile.clampOffset
import com.drift.stream.domain.profile.clampScale
import com.drift.stream.domain.profile.initialCropState
import com.drift.stream.domain.profile.minScale
import com.drift.stream.presentation.common.NexusIconButton
import com.drift.stream.presentation.theme.NexusTheme

private val TOP_BAR_HEIGHT = 56.dp
private val CIRCLE_MARGIN = 32.dp
private val CIRCLE_STROKE_WIDTH = 2.dp
private val HINT_BOTTOM_PADDING = 32.dp
private const val DIM_ALPHA = 0.65f
private const val OUTLINE_ALPHA = 0.8f
private const val MIN_WINDOW_SIZE = 100

@Composable
fun PhotoCropScreen(
    bitmap: Bitmap,
    onDone: (CropState, Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCancel)

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        val density = LocalDensity.current
        val windowSizePx =
            remember(maxWidth, maxHeight, density) {
                with(density) {
                    val shortestSide = minOf(maxWidth, maxHeight) - CIRCLE_MARGIN * 2
                    shortestSide.roundToPx().coerceAtLeast(MIN_WINDOW_SIZE)
                }
            }

        val bounds =
            remember(bitmap.width, bitmap.height, windowSizePx) {
                CropBounds(
                    imgW = bitmap.width,
                    imgH = bitmap.height,
                    window = windowSizePx,
                )
            }

        val initial = remember(bounds) { initialCropState(bounds.imgW, bounds.imgH, bounds.window) }

        var scale by rememberSaveable { mutableFloatStateOf(0f) }
        var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
        var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

        val currentState =
            remember(scale, offsetX, offsetY, bounds) {
                if (scale <= 0f) {
                    scale = initial.scale
                    offsetX = initial.offsetX
                    offsetY = initial.offsetY
                    initial
                } else {
                    val min = minScale(bounds.imgW, bounds.imgH, bounds.window)
                    val clampedScale = clampScale(scale, min)
                    clampOffset(
                        CropState(clampedScale, offsetX, offsetY),
                        imgW = bounds.imgW,
                        imgH = bounds.imgH,
                        window = bounds.window,
                    )
                }
            }

        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

        // Gestures and image rendering
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(bounds) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val updated =
                                applyGesture(
                                    state = currentState,
                                    gesture =
                                        com.drift.stream.domain.profile.CropGesture(
                                            zoom = zoom,
                                            panX = pan.x,
                                            panY = pan.y,
                                        ),
                                    bounds = bounds,
                                )
                            scale = updated.scale
                            offsetX = updated.offsetX
                            offsetY = updated.offsetY
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = currentState.scale
                        scaleY = currentState.scale
                        translationX = currentState.offsetX
                        translationY = currentState.offsetY
                    },
            )

            // Dim overlay with circular crop hole
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = windowSizePx.toFloat() / 2f
                val layerPaint = Paint()
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                drawRect(color = Color.Black.copy(alpha = DIM_ALPHA))
                drawCircle(
                    color = Color.Transparent,
                    radius = radius,
                    center = center,
                    blendMode = BlendMode.Clear,
                )
                drawContext.canvas.restore()

                drawCircle(
                    color = Color.White.copy(alpha = OUTLINE_ALPHA),
                    radius = radius,
                    center = center,
                    style = Stroke(width = CIRCLE_STROKE_WIDTH.toPx()),
                )
            }
        }

        // Top bar
        CropTopBar(
            onCancel = onCancel,
            onDone = { onDone(currentState, windowSizePx) },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Bottom hint
        Text(
            text = stringResource(R.string.photo_crop_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = HINT_BOTTOM_PADDING)
                    .fillMaxWidth(),
        )
    }
}

@Composable
private fun CropTopBar(
    onCancel: () -> Unit,
    onDone: () -> Unit,
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
                tint = Color.White,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.photo_crop_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )

        Spacer(modifier = Modifier.weight(1f))

        NexusIconButton(onClick = onDone) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.avatar_action_done),
                tint = Color.White,
            )
        }
    }
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
