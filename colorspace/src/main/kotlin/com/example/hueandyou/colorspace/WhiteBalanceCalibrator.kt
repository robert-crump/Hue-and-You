package com.example.hueandyou.colorspace

/** A von-Kries-style diagonal correction: scales each linear-light channel independently. */
data class WhiteBalanceCorrection(val scaleR: Double, val scaleG: Double, val scaleB: Double) {
    fun apply(linear: LinearRgb): LinearRgb = LinearRgb(
        r = linear.r * scaleR,
        g = linear.g * scaleG,
        b = linear.b * scaleB,
    )

    fun apply(argb: Int): Int = linearRgbToArgb(apply(argbToLinearRgb(argb)))
}

enum class WhiteBalanceFailureReason {
    /** The sample circle contained no pixels inside the image bounds. */
    NO_SAMPLED_PIXELS,
}

sealed interface WhiteBalanceResult {
    data class Success(val correction: WhiteBalanceCorrection, val sampledRegion: CircleRegion) :
        WhiteBalanceResult
    data class Failure(val reason: WhiteBalanceFailureReason) : WhiteBalanceResult
}

/**
 * Samples a circular patch of a photo around a user tap (expected to land on a white sheet of
 * paper) and derives a correction that maps that patch's average color to neutral gray.
 */
object WhiteBalanceCalibrator {
    fun calibrate(
        pixels: PixelSource,
        tapX: Int,
        tapY: Int,
        sampleRadiusFraction: Double = CalibrationConfig.WHITE_SAMPLE_RADIUS_FRACTION,
    ): WhiteBalanceResult {
        val radius = (sampleRadiusFraction * minOf(pixels.width, pixels.height))
            .toInt()
            .coerceAtLeast(1)
        val region = CircleRegion(tapX, tapY, radius)

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        val minX = (tapX - radius).coerceAtLeast(0)
        val maxX = (tapX + radius).coerceAtMost(pixels.width - 1)
        val minY = (tapY - radius).coerceAtLeast(0)
        val maxY = (tapY + radius).coerceAtMost(pixels.height - 1)

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                if (!region.contains(x, y)) continue
                val linear = argbToLinearRgb(pixels.pixelAt(x, y))
                sumR += linear.r
                sumG += linear.g
                sumB += linear.b
                count++
            }
        }

        if (count == 0) return WhiteBalanceResult.Failure(WhiteBalanceFailureReason.NO_SAMPLED_PIXELS)

        val avgR = sumR / count
        val avgG = sumG / count
        val avgB = sumB / count
        val target = (avgR + avgG + avgB) / 3.0

        val correction = WhiteBalanceCorrection(
            scaleR = safeScale(target, avgR),
            scaleG = safeScale(target, avgG),
            scaleB = safeScale(target, avgB),
        )
        return WhiteBalanceResult.Success(correction, region)
    }

    private fun safeScale(target: Double, average: Double): Double =
        if (average <= 0.0) 1.0 else target / average
}
