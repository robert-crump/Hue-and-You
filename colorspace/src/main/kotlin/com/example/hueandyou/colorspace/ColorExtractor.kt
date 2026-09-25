package com.example.hueandyou.colorspace

import kotlin.math.roundToInt
import me.tatarka.google.material.quantize.QuantizerCelebi

data class ExtractedColor(val argb: Int, val share: Double)

/**
 * Extracts colors from a photo, quantizing with Celebi - Wu boxes refined by weighted k-means,
 * the same quantizer Material Color Utilities uses to pick theme colors from a photo.
 */
object ColorExtractor {
    /**
     * Every quantized cluster in [region] (the center box by default), largest share first, with
     * no minimum-share filter - the caller decides which of these are worth keeping.
     */
    fun extractCandidates(
        pixels: PixelSource,
        region: RectRegion = RectRegion.centerBox(pixels.width, pixels.height, CalibrationConfig.CENTER_BOX_FRACTION),
        clusterCount: Int = CalibrationConfig.QUANTIZER_CLUSTER_COUNT,
    ): List<ExtractedColor> {
        val buffer = collectPixels(pixels, region.clampTo(pixels.width, pixels.height))
        val quantized = QuantizerCelebi.quantize(buffer, clusterCount)
        val total = quantized.values.sum().coerceAtLeast(1)
        return quantized.entries
            .sortedByDescending { it.value }
            .map { ExtractedColor(it.key, it.value.toDouble() / total) }
    }

    /**
     * The largest-share color in [region] (the center box by default), so the user gets from
     * photo to result without picking a spot themselves.
     */
    fun extractMainColor(
        pixels: PixelSource,
        region: RectRegion = RectRegion.centerBox(pixels.width, pixels.height, CalibrationConfig.CENTER_BOX_FRACTION),
        clusterCount: Int = CalibrationConfig.QUANTIZER_CLUSTER_COUNT,
    ): Int = extractCandidates(pixels, region, clusterCount).first().argb

    /**
     * The dominant color in a circle of radius [radiusFraction] of the image's short edge,
     * centered at ([x], [y]) - used for tap-to-pick.
     */
    fun extractColorAtPoint(
        pixels: PixelSource,
        x: Int,
        y: Int,
        radiusFraction: Double = CalibrationConfig.TAP_SAMPLE_RADIUS_FRACTION,
        clusterCount: Int = CalibrationConfig.QUANTIZER_CLUSTER_COUNT,
    ): Int {
        val radius = (minOf(pixels.width, pixels.height) * radiusFraction).roundToInt().coerceAtLeast(1)
        val bounds = RectRegion(x - radius, y - radius, x + radius + 1, y + radius + 1)
            .clampTo(pixels.width, pixels.height)

        val buffer = IntArray(maxOf(0, bounds.right - bounds.left) * maxOf(0, bounds.bottom - bounds.top))
        var count = 0
        val radiusSquared = radius * radius
        for (py in bounds.top until bounds.bottom) {
            for (px in bounds.left until bounds.right) {
                val dx = px - x
                val dy = py - y
                if (dx * dx + dy * dy <= radiusSquared) buffer[count++] = pixels.pixelAt(px, py)
            }
        }

        val quantized = QuantizerCelebi.quantize(if (count == buffer.size) buffer else buffer.copyOf(count), clusterCount)
        return quantized.entries.maxBy { it.value }.key
    }

    /**
     * Up to [maxAlternatives] of [candidates] (ranked, most-preferred first) that are at least
     * [minDeltaE] apart from [currentArgb] and from each other - so the chip row never shows two
     * colors a user couldn't tell apart.
     */
    fun selectAlternatives(
        candidates: List<Int>,
        currentArgb: Int,
        maxAlternatives: Int = CalibrationConfig.ALTERNATIVE_MAX_COUNT,
        minDeltaE: Double = CalibrationConfig.ALTERNATIVE_MIN_DELTA_E,
    ): List<Int> {
        val chosenLabs = mutableListOf(argbToLab(currentArgb))
        val alternatives = mutableListOf<Int>()
        for (argb in candidates) {
            if (alternatives.size >= maxAlternatives) break
            val lab = argbToLab(argb)
            if (chosenLabs.all { ciede2000(it, lab) >= minDeltaE }) {
                alternatives += argb
                chosenLabs += lab
            }
        }
        return alternatives
    }

    private fun collectPixels(pixels: PixelSource, bounds: RectRegion): IntArray {
        val buffer = IntArray(maxOf(0, bounds.right - bounds.left) * maxOf(0, bounds.bottom - bounds.top))
        var count = 0
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                buffer[count++] = pixels.pixelAt(x, y)
            }
        }
        return if (count == buffer.size) buffer else buffer.copyOf(count)
    }
}
