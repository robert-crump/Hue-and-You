package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteBalanceCalibratorTest {

    @Test
    fun warmTintedWhitePatchIsCorrectedToNeutral() {
        // A warm off-white: more red and green than blue, as a white sheet might look under
        // incandescent light.
        val warmWhite = 0xFFFFE0C0.toInt()
        val pixels = solidImage(width = 40, height = 40, argb = warmWhite)

        val result = WhiteBalanceCalibrator.calibrate(pixels, tapX = 20, tapY = 20)

        val success = result as? WhiteBalanceResult.Success
        assertTrue("Expected a successful calibration, got $result", success != null)

        val correctedLinear = success!!.correction.apply(argbToLinearRgb(warmWhite))
        assertEquals(correctedLinear.r, correctedLinear.g, 1e-6)
        assertEquals(correctedLinear.g, correctedLinear.b, 1e-6)
    }

    @Test
    fun tapOutsideImageFailsWithNoSampledPixels() {
        val pixels = solidImage(width = 40, height = 40, argb = 0xFFFFFFFF.toInt())

        val result = WhiteBalanceCalibrator.calibrate(pixels, tapX = 1000, tapY = 1000)

        assertEquals(WhiteBalanceResult.Failure(WhiteBalanceFailureReason.NO_SAMPLED_PIXELS), result)
    }

    private fun solidImage(width: Int, height: Int, argb: Int): PixelSource =
        IntArrayPixelSource(width, height, IntArray(width * height) { argb })
}
