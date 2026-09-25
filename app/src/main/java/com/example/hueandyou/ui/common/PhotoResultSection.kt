package com.example.hueandyou.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.CalibrationConfig

/**
 * The photo at the top of a result page, with a marker showing where the current color was
 * sampled from - the center box for the auto-pick or a chip-pick (null [sampleX]/[sampleY]), or
 * a ring at the tap point for a tap-pick. When [onTap] is non-null, tapping the photo reports a
 * normalized point in [0, 1] to it (the caller is responsible for running the off-main-thread
 * sampling); when null, the photo is display-only.
 */
@Composable
internal fun PhotoResultSection(
    photo: Bitmap,
    sampleX: Double?,
    sampleY: Double?,
    maxHeightFraction: Float,
    onTap: ((x: Double, y: Double) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(photo) { photo.asImageBitmap() }
    val bitmapAspectRatio = photo.width.toFloat() / photo.height.toFloat()
    var displaySizePx by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxAllowedHeight = maxHeight * maxHeightFraction
        val widthConstrainedHeight = maxWidth / bitmapAspectRatio
        val displayHeight = minOf(maxAllowedHeight, widthConstrainedHeight)
        val displayWidth = displayHeight * bitmapAspectRatio

        Box(modifier = Modifier.size(displayWidth, displayHeight)) {
            Image(
                bitmap = imageBitmap,
                contentDescription = stringResource(R.string.result_photo_content_description),
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { displaySizePx = it }
                    .let { imageModifier ->
                        if (onTap == null) return@let imageModifier
                        imageModifier.pointerInput(photo) {
                            detectTapGestures { offset ->
                                val size = displaySizePx
                                if (size.width == 0 || size.height == 0) return@detectTapGestures
                                val x = (offset.x / size.width).toDouble().coerceIn(0.0, 1.0)
                                val y = (offset.y / size.height).toDouble().coerceIn(0.0, 1.0)
                                onTap(x, y)
                            }
                        }
                    }
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (sampleX != null && sampleY != null) {
                    val radius = (minOf(size.width, size.height) * CalibrationConfig.TAP_SAMPLE_RADIUS_FRACTION.toFloat())
                        .coerceAtLeast(8f)
                    drawMarkerRing(center = Offset(size.width * sampleX.toFloat(), size.height * sampleY.toFloat()), radius = radius)
                } else {
                    val fraction = CalibrationConfig.CENTER_BOX_FRACTION.toFloat()
                    val left = size.width * (1f - fraction) / 2f
                    val top = size.height * (1f - fraction) / 2f
                    drawMarkerRect(
                        topLeft = Offset(left, top),
                        boxSize = Size(size.width * fraction, size.height * fraction),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMarkerRing(center: Offset, radius: Float) {
    drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = radius, center = center, style = Stroke(width = 6f))
    drawCircle(color = Color.White, radius = radius, center = center, style = Stroke(width = 3f))
}

internal fun DrawScope.drawMarkerRect(topLeft: Offset, boxSize: Size) {
    drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = topLeft, size = boxSize, style = Stroke(width = 6f))
    drawRect(color = Color.White, topLeft = topLeft, size = boxSize, style = Stroke(width = 3f))
}

/**
 * The photo's top colors in a fixed order; the chip matching [currentArgb] (if any - a tap-pick
 * may match none) gets the highlight, so a re-pick only moves the border.
 */
@Composable
internal fun ColorChipRow(
    chipColorsArgb: List<Int>,
    currentArgb: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        chipColorsArgb.forEach { argb ->
            ColorChip(argb = argb, selected = argb == currentArgb, onClick = { onPick(argb) })
        }
    }
}

@Composable
private fun ColorChip(argb: Int, selected: Boolean, onClick: () -> Unit) {
    ColorCircle(
        argb = argb,
        size = 48.dp,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        onClick = onClick,
    )
}
