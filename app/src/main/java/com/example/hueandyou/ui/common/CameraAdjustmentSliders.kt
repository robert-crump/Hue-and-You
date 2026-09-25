package com.example.hueandyou.ui.common

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
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
 * White-balance and exposure sliders for the viewfinder, always visible below the center box.
 * Both apply to the preview and to the captured photo; they stay disabled until a [camera] is
 * bound or if the device doesn't support the adjustment.
 */
@Composable
internal fun CameraAdjustmentSliders(camera: Camera?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        WhiteBalanceSlider(camera)
        ExposureSlider(camera)
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun WhiteBalanceSlider(camera: Camera?) {
    val modes = remember(camera) { camera?.let(::availableAwbModes) ?: listOf(CameraMetadata.CONTROL_AWB_MODE_AUTO) }
    var position by remember(camera) { mutableFloatStateOf(0f) }
    val selected = position.roundToInt().coerceIn(0, modes.lastIndex)

    LaunchedEffect(camera, selected) {
        val boundCamera = camera ?: return@LaunchedEffect
        Camera2CameraControl.from(boundCamera.cameraControl).setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, modes[selected])
                .build()
        )
    }

    SliderRow(
        icon = Icons.Filled.WbSunny,
        contentDescription = stringResource(R.string.camera_white_balance_content_description),
        label = stringResource(awbLabel(modes[selected])),
        value = position,
        onValueChange = { position = it },
        valueRange = 0f..modes.lastIndex.coerceAtLeast(1).toFloat(),
        steps = (modes.size - 2).coerceAtLeast(0),
        enabled = modes.size > 1,
    )
}

@Composable
private fun ExposureSlider(camera: Camera?) {
    val exposureState = camera?.cameraInfo?.exposureState
    val range = exposureState?.exposureCompensationRange
    val supported = exposureState?.isExposureCompensationSupported == true && range != null && range.upper > range.lower
    var position by remember(camera) { mutableFloatStateOf(0f) }
    val index = position.roundToInt()

    LaunchedEffect(camera, index) {
        if (supported) camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    SliderRow(
        icon = Icons.Filled.Exposure,
        contentDescription = stringResource(R.string.camera_exposure_content_description),
        label = if (supported) "%+d EV".format(index) else "0 EV",
        value = position,
        onValueChange = { position = it },
        valueRange = if (supported && range != null) range.lower.toFloat()..range.upper.toFloat() else -1f..1f,
        steps = if (supported && range != null) range.upper - range.lower - 1 else 0,
        enabled = supported,
    )
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    contentDescription: String,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(88.dp),
        )
    }
}

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
