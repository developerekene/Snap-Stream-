package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

private enum class DragTarget {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE
}

@Composable
fun CropOverlayCanvas(
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    aspectRatio: Float?,
    onCropChange: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val currentLeft by rememberUpdatedState(cropLeft)
    val currentTop by rememberUpdatedState(cropTop)
    val currentRight by rememberUpdatedState(cropRight)
    val currentBottom by rememberUpdatedState(cropBottom)
    val currentAspect by rememberUpdatedState(aspectRatio)
    val currentOnChange by rememberUpdatedState(onCropChange)

    var activeTarget by remember { mutableStateOf(DragTarget.NONE) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("crop_overlay_canvas")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val l = currentLeft * w
                        val t = currentTop * h
                        val r = currentRight * w
                        val b = currentBottom * h
                        val hitRadius = 56.dp.toPx()

                        activeTarget = when {
                            hypot(startOffset.x - l, startOffset.y - t) <= hitRadius -> DragTarget.TOP_LEFT
                            hypot(startOffset.x - r, startOffset.y - t) <= hitRadius -> DragTarget.TOP_RIGHT
                            hypot(startOffset.x - l, startOffset.y - b) <= hitRadius -> DragTarget.BOTTOM_LEFT
                            hypot(startOffset.x - r, startOffset.y - b) <= hitRadius -> DragTarget.BOTTOM_RIGHT
                            startOffset.x in l..r && startOffset.y in t..b -> DragTarget.MOVE
                            else -> DragTarget.NONE
                        }
                    },
                    onDragEnd = { activeTarget = DragTarget.NONE },
                    onDragCancel = { activeTarget = DragTarget.NONE },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val dx = dragAmount.x / w
                        val dy = dragAmount.y / h
                        val minSize = 0.16f

                        var l = currentLeft
                        var t = currentTop
                        var r = currentRight
                        var b = currentBottom

                        when (activeTarget) {
                            DragTarget.MOVE -> {
                                val boxW = r - l
                                val boxH = b - t
                                l = (l + dx).coerceIn(0f, 1f - boxW)
                                t = (t + dy).coerceIn(0f, 1f - boxH)
                                r = l + boxW
                                b = t + boxH
                            }
                            DragTarget.TOP_LEFT -> {
                                l = (l + dx).coerceIn(0f, r - minSize)
                                t = (t + dy).coerceIn(0f, b - minSize)
                                if (currentAspect != null) {
                                    val boxAspect = currentAspect!! * (h / w)
                                    val desiredH = ((r - l) / boxAspect).coerceIn(minSize, b)
                                    t = (b - desiredH).coerceIn(0f, b - minSize)
                                }
                            }
                            DragTarget.TOP_RIGHT -> {
                                r = (r + dx).coerceIn(l + minSize, 1f)
                                t = (t + dy).coerceIn(0f, b - minSize)
                                if (currentAspect != null) {
                                    val boxAspect = currentAspect!! * (h / w)
                                    val desiredH = ((r - l) / boxAspect).coerceIn(minSize, b)
                                    t = (b - desiredH).coerceIn(0f, b - minSize)
                                }
                            }
                            DragTarget.BOTTOM_LEFT -> {
                                l = (l + dx).coerceIn(0f, r - minSize)
                                b = (b + dy).coerceIn(t + minSize, 1f)
                                if (currentAspect != null) {
                                    val boxAspect = currentAspect!! * (h / w)
                                    val desiredH = ((r - l) / boxAspect).coerceIn(minSize, 1f - t)
                                    b = (t + desiredH).coerceIn(t + minSize, 1f)
                                }
                            }
                            DragTarget.BOTTOM_RIGHT -> {
                                r = (r + dx).coerceIn(l + minSize, 1f)
                                b = (b + dy).coerceIn(t + minSize, 1f)
                                if (currentAspect != null) {
                                    val boxAspect = currentAspect!! * (h / w)
                                    val desiredH = ((r - l) / boxAspect).coerceIn(minSize, 1f - t)
                                    b = (t + desiredH).coerceIn(t + minSize, 1f)
                                }
                            }
                            DragTarget.NONE -> Unit
                        }
                        currentOnChange(l, t, r, b)
                    }
                )
            }
    ) {
        val l = cropLeft * size.width
        val t = cropTop * size.height
        val r = cropRight * size.width
        val b = cropBottom * size.height
        val cropRect = Rect(l, t, r, b)

        // Dim outside areas
        val scrim = Color.Black.copy(alpha = 0.58f)
        drawRect(scrim, Offset.Zero, Size(size.width, t))
        drawRect(scrim, Offset(0f, b), Size(size.width, size.height - b))
        drawRect(scrim, Offset(0f, t), Size(l, b - t))
        drawRect(scrim, Offset(r, t), Size(size.width - r, b - t))

        // Rule of thirds dashed lines
        val thirdW = cropRect.width / 3f
        val thirdH = cropRect.height / 3f
        val gridColor = Color.White.copy(alpha = 0.55f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        for (i in 1..2) {
            drawLine(
                color = gridColor,
                start = Offset(l + thirdW * i, t),
                end = Offset(l + thirdW * i, b),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
            drawLine(
                color = gridColor,
                start = Offset(l, t + thirdH * i),
                end = Offset(r, t + thirdH * i),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
        }

        // Crop box outline
        drawRect(
            color = Color.White,
            topLeft = cropRect.topLeft,
            size = cropRect.size,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Electric Blue corner brackets
        val cornerLen = 20.dp.toPx().coerceAtMost(cropRect.width / 3f).coerceAtMost(cropRect.height / 3f)
        val strokeW = 3.5.dp.toPx()
        val cornersPath = Path().apply {
            // Top-Left
            moveTo(l, t + cornerLen)
            lineTo(l, t)
            lineTo(l + cornerLen, t)
            // Top-Right
            moveTo(r - cornerLen, t)
            lineTo(r, t)
            lineTo(r, t + cornerLen)
            // Bottom-Left
            moveTo(l, b - cornerLen)
            lineTo(l, b)
            lineTo(l + cornerLen, b)
            // Bottom-Right
            moveTo(r - cornerLen, b)
            lineTo(r, b)
            lineTo(r, b - cornerLen)
        }
        drawPath(
            path = cornersPath,
            color = accentColor,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}
