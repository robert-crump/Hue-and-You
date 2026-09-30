package com.example.hueandyou.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomTest {

    @Test
    fun nextZoomRatio_multipliesWithinRange() {
        assertEquals(3f, nextZoomRatio(current = 2f, scale = 1.5f, max = 8f), 1e-6f)
    }

    @Test
    fun nextZoomRatio_clampsAtOneX() {
        assertEquals(1f, nextZoomRatio(current = 1.2f, scale = 0.5f, max = 8f), 1e-6f)
    }

    @Test
    fun nextZoomRatio_clampsAtDeviceMax() {
        assertEquals(8f, nextZoomRatio(current = 6f, scale = 2f, max = 8f), 1e-6f)
    }

    @Test
    fun nextZoomRatio_maxBelowOneXPinsAtOneX() {
        assertEquals(1f, nextZoomRatio(current = 1f, scale = 2f, max = 0.6f), 1e-6f)
    }

    @Test
    fun nextZoomRatio_actionStepsRoundTrip() {
        val zoomedIn = nextZoomRatio(current = 2f, scale = ZOOM_ACTION_STEP, max = 10f)
        assertEquals(2f, nextZoomRatio(zoomedIn, 1f / ZOOM_ACTION_STEP, max = 10f), 1e-5f)
    }
}
