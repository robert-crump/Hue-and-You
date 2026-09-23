package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteBalanceCalibratorTest {

    @Test
    fun warmTintedWhitePatchIsCorrectedToNeutral() {
        // A warm off-white: more red and green than blue, as a white sheet might look under
        // incandescent light. Kept comfortably clear of the clipped/not-white thresholds so this
        // stays a happy-path case (see the dedicated failure tests below for those).
        val warmWhite = 0xFFF5E6D3.toInt()
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

    @Test
    fun overexposedPatchFailsAsClipped() {
        // Every pixel blown out to pure white - well over the 5% clipped-pixel threshold.
        val pixels = solidImage(width = 40, height = 40, argb = 0xFFFFFFFF.toInt())

        val result = WhiteBalanceCalibrator.calibrate(pixels, tapX = 20, tapY = 20)

        assertEquals(WhiteBalanceResult.Failure(WhiteBalanceFailureReason.CLIPPED), result)
    }

    @Test
    fun darkPatchFailsAsTooDark() {
        // A neutral gray patch, far too dim to be a lit white sheet.
        val pixels = solidImage(width = 40, height = 40, argb = 0xFF303030.toInt())

        val result = WhiteBalanceCalibrator.calibrate(pixels, tapX = 20, tapY = 20)

        assertEquals(WhiteBalanceResult.Failure(WhiteBalanceFailureReason.TOO_DARK), result)
    }

    @Test
    fun saturatedPatchFailsAsNotWhite() {
        // A bright but clearly colored (not neutral) patch - too much chroma to be paper.
        val pixels = solidImage(width = 40, height = 40, argb = 0xFFDC786E.toInt())

        val result = WhiteBalanceCalibrator.calibrate(pixels, tapX = 20, tapY = 20)

        assertEquals(WhiteBalanceResult.Failure(WhiteBalanceFailureReason.NOT_WHITE), result)
    }

    private fun solidImage(width: Int, height: Int, argb: Int): PixelSource =
        IntArrayPixelSource(width, height, IntArray(width * height) { argb })
}
