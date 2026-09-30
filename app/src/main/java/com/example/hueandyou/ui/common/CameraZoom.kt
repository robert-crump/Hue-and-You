package com.example.hueandyou.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Zoom never goes below 1x, even on devices whose widest lens would allow it. */
internal const val MIN_ZOOM_RATIO = 1f

/** How far one TalkBack "Zoom in" / "Zoom out" action scales the current ratio. */
internal const val ZOOM_ACTION_STEP = 1.5f

/** [current] scaled by [scale], clamped to [MIN_ZOOM_RATIO]..[max] (a [max] below 1x pins it at 1x). */
internal fun nextZoomRatio(current: Float, scale: Float, max: Float): Float =
    (current * scale).coerceIn(MIN_ZOOM_RATIO, maxOf(max, MIN_ZOOM_RATIO))

/**
 * Reports pinch scale factors to [onPinch] from anywhere in this element, children included. The
 * pointers are watched in the Initial pass, so once a second finger lands the whole gesture is
 * consumed before children (e.g. sliders) see it; one-finger gestures pass through untouched.
 */
internal fun Modifier.pinchToZoom(
    onPinch: (Float) -> Unit,
    onPinchingChange: (Boolean) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                if (!pinching) {
                    pinching = true
                    onPinchingChange(true)
                }
                val zoom = event.calculateZoom()
                if (zoom != 1f) onPinch(zoom)
            }
            // Keep consuming after a finger lifts so the remaining one doesn't start a slider drag.
            if (pinching) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        if (pinching) onPinchingChange(false)
    }
}
