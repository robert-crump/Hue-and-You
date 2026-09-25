package com.example.hueandyou.colorspace

import me.tatarka.google.material.quantize.QuantizerCelebi

data class ExtractedColor(val argb: Int, val share: Double)

/**
 * Extracts the dominant color from a photo, quantizing with Celebi - Wu boxes refined by weighted
 * k-means, the same quantizer Material Color Utilities uses to pick theme colors from a photo.
 */
object ColorExtractor {
    /**
     * The largest-share color in [region] (the center box by default), so the user gets from
     * photo to result without picking a spot themselves.
     */
    fun extractMainColor(
        pixels: PixelSource,
        region: RectRegion = RectRegion.centerBox(pixels.width, pixels.height, CalibrationConfig.CENTER_BOX_FRACTION),
        clusterCount: Int = CalibrationConfig.QUANTIZER_CLUSTER_COUNT,
    ): Int {
        val bounds = region.clampTo(pixels.width, pixels.height)

        val buffer = IntArray(maxOf(0, bounds.right - bounds.left) * maxOf(0, bounds.bottom - bounds.top))
        var count = 0
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                buffer[count++] = pixels.pixelAt(x, y)
            }
        }

        val quantized = QuantizerCelebi.quantize(if (count == buffer.size) buffer else buffer.copyOf(count), clusterCount)
        return quantized.entries.maxBy { it.value }.key
    }
}
