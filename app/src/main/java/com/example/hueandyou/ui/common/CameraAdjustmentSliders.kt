package com.example.hueandyou.ui.common

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.CalibrationConfig
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Auto first, then the presets from warm to cool light - the order the white-balance slider steps through. */
private val AWB_PRESET_ORDER = listOf(
    CameraMetadata.CONTROL_AWB_MODE_AUTO,
    CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT,
    CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT,
    CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT,
    CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
    CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
    CameraMetadata.CONTROL_AWB_MODE_TWILIGHT,
    CameraMetadata.CONTROL_AWB_MODE_SHADE,
)

/**
 * White-balance slider in the strip left of the center box and exposure slider in the strip right
 * of it, each vertically centered. Both apply to the preview and to the captured photo; they stay
 * disabled until a [camera] is bound or if the device doesn't support the adjustment.
 */
@Composable
internal fun CameraAdjustmentSliders(camera: Camera?, modifier: Modifier = Modifier) {
    val stripWidth = (1f - CalibrationConfig.CENTER_BOX_FRACTION.toFloat()) / 2f
    Box(modifier = modifier) {
        WhiteBalanceSlider(
            camera,
            Modifier.align(Alignment.CenterStart).fillMaxWidth(stripWidth).padding(horizontal = 4.dp),
        )
        ExposureSlider(
            camera,
            Modifier.align(Alignment.CenterEnd).fillMaxWidth(stripWidth).padding(horizontal = 4.dp),
        )
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun WhiteBalanceSlider(camera: Camera?, modifier: Modifier) {
    val modes = remember(camera) { camera?.let(::availableAwbModes) ?: listOf(CameraMetadata.CONTROL_AWB_MODE_AUTO) }
    var position by remember(camera) { mutableFloatStateOf(0f) }
    val selected = position.roundToInt().coerceIn(0, modes.lastIndex)
    val whiteBalanceDescription = stringResource(R.string.camera_white_balance_content_description)

    LaunchedEffect(camera, selected) {
        val boundCamera = camera ?: return@LaunchedEffect
        Camera2CameraControl.from(boundCamera.cameraControl).setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, modes[selected])
                .build()
        )
    }

    SliderColumn(
        modifier = modifier,
        icon = {
            Text(
                text = stringResource(R.string.camera_white_balance_abbreviation),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics {
                    contentDescription = whiteBalanceDescription
                },
            )
        },
        label = stringResource(awbLabel(modes[selected])),
        value = position,
        onValueChange = { position = it },
        valueRange = 0f..modes.lastIndex.coerceAtLeast(1).toFloat(),
        steps = (modes.size - 2).coerceAtLeast(0),
        enabled = modes.size > 1,
    )
}

/** Exposure is offered in 1/3 EV notches from -3 to +3 EV, as far as the device's range allows. */
private const val EXPOSURE_NOTCH_EV = 1f / 3f
private const val MAX_EXPOSURE_NOTCHES = 9

@Composable
private fun ExposureSlider(camera: Camera?, modifier: Modifier) {
    val exposureState = camera?.cameraInfo?.exposureState
    val range = exposureState?.exposureCompensationRange
    val evPerIndex = exposureState?.exposureCompensationStep?.toFloat() ?: 1f
    val indicesPerNotch = maxOf(1, (EXPOSURE_NOTCH_EV / evPerIndex).roundToInt())
    val lowerNotch = range?.let { maxOf(ceil(it.lower.toFloat() / indicesPerNotch).toInt(), -MAX_EXPOSURE_NOTCHES) } ?: 0
    val upperNotch = range?.let { minOf(floor(it.upper.toFloat() / indicesPerNotch).toInt(), MAX_EXPOSURE_NOTCHES) } ?: 0
    val supported = exposureState?.isExposureCompensationSupported == true && upperNotch > lowerNotch
    var position by remember(camera) { mutableFloatStateOf(0f) }
    val notch = position.roundToInt()
    val index = notch * indicesPerNotch

    LaunchedEffect(camera, index) {
        if (supported) camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    val exposureDescription = stringResource(R.string.camera_exposure_content_description)
    SliderColumn(
        modifier = modifier,
        icon = { Icon(Icons.Filled.Exposure, contentDescription = exposureDescription, tint = Color.White, modifier = Modifier.size(20.dp)) },
        label = if (supported) "%.1f EV".format(index * evPerIndex) else "0 EV",
        value = position,
        onValueChange = { position = it },
        valueRange = if (supported) lowerNotch.toFloat()..upperNotch.toFloat() else -1f..1f,
        steps = if (supported) upperNotch - lowerNotch - 1 else 0,
        enabled = supported,
    )
}

/** Vertical slider (max at the top) with its icon above and current value below. */
@Composable
private fun SliderColumn(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        icon()
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .height(SLIDER_LENGTH)
                .verticalSliderLayout(),
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            // Long labels may extend past the column rather than wrap mid-word.
            modifier = Modifier.wrapContentWidth(unbounded = true),
        )
    }
}

private val SLIDER_LENGTH = 240.dp

/**
 * Turns a horizontal slider by 270 degrees so it runs bottom (min) to top (max). The layout swaps
 * width and height first, so the rotated slider takes up a tall, narrow slot.
 */
private fun Modifier.verticalSliderLayout(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2),
            )
        }
    }
    .graphicsLayer(rotationZ = 270f)

@OptIn(ExperimentalCamera2Interop::class)
private fun availableAwbModes(camera: Camera): List<Int> {
    val supported = Camera2CameraInfo.from(camera.cameraInfo)
        .getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
        ?.toSet()
        ?: return listOf(CameraMetadata.CONTROL_AWB_MODE_AUTO)
    return AWB_PRESET_ORDER.filter { it == CameraMetadata.CONTROL_AWB_MODE_AUTO || it in supported }
}

private fun awbLabel(mode: Int): Int = when (mode) {
    CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> R.string.camera_white_balance_incandescent
    CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT -> R.string.camera_white_balance_warm_fluorescent
    CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> R.string.camera_white_balance_fluorescent
    CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> R.string.camera_white_balance_daylight
    CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> R.string.camera_white_balance_cloudy
    CameraMetadata.CONTROL_AWB_MODE_TWILIGHT -> R.string.camera_white_balance_twilight
    CameraMetadata.CONTROL_AWB_MODE_SHADE -> R.string.camera_white_balance_shade
    else -> R.string.camera_white_balance_auto
}
