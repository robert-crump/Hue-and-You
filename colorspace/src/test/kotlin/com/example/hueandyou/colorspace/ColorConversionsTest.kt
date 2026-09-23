package com.example.hueandyou.colorspace

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorConversionsTest {

    private val sampleColors = listOf(
        0xFF000000.toInt(), // black
        0xFFFFFFFF.toInt(), // white
        0xFFFF0000.toInt(), // red
        0xFF00FF00.toInt(), // green
        0xFF0000FF.toInt(), // blue
        0xFFFFFF00.toInt(), // yellow
        0xFF00FFFF.toInt(), // cyan
        0xFFFF00FF.toInt(), // magenta
        0xFF808080.toInt(), // mid gray
        0xFF1A2B3C.toInt(),
        0xFFE07A5F.toInt(),
        0xFF3D405B.toInt(),
        0xFF81B29A.toInt(),
        0xFFF2CC8F.toInt(),
    )

    @Test
    fun srgbToLinearRoundTrips() {
        for (argb in sampleColors) {
            val roundTripped = linearRgbToArgb(argbToLinearRgb(argb))
            assertArgbCloseTo(argb, roundTripped, tolerance = 1)
        }
    }

    @Test
    fun srgbToLabRoundTrips() {
        for (argb in sampleColors) {
            val roundTripped = labToArgb(argbToLab(argb))
            assertArgbCloseTo(argb, roundTripped, tolerance = 1)
        }
    }

    @Test
    fun srgbToHctRoundTrips() {
        for (argb in sampleColors) {
            val roundTripped = hctToArgb(argbToHct(argb))
            assertArgbCloseTo(argb, roundTripped, tolerance = 4)
        }
    }

    @Test
    fun labOfBlackAndWhiteMatchesKnownValues() {
        val black = argbToLab(0xFF000000.toInt())
        assertEquals(0.0, black.l, 0.01)
        assertEquals(0.0, black.a, 0.01)
        assertEquals(0.0, black.b, 0.01)

        val white = argbToLab(0xFFFFFFFF.toInt())
        assertEquals(100.0, white.l, 0.01)
        assertEquals(0.0, white.a, 0.05)
        assertEquals(0.0, white.b, 0.05)
    }

    private fun assertArgbCloseTo(expected: Int, actual: Int, tolerance: Int) {
        assertChannelCloseTo("red", (expected shr 16) and 0xFF, (actual shr 16) and 0xFF, tolerance)
        assertChannelCloseTo("green", (expected shr 8) and 0xFF, (actual shr 8) and 0xFF, tolerance)
        assertChannelCloseTo("blue", expected and 0xFF, actual and 0xFF, tolerance)
    }

    private fun assertChannelCloseTo(name: String, expected: Int, actual: Int, tolerance: Int) {
        assertTrue(
            "$name channel expected $expected, got $actual (tolerance $tolerance)",
            abs(expected - actual) <= tolerance,
        )
    }
}
