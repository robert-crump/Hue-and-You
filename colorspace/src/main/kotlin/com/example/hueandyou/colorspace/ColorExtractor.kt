package com.example.hueandyou.colorspace

import me.tatarka.google.material.quantize.QuantizerCelebi

data class ExtractedColor(val argb: Int, val share: Double)

data class ColorExtractionResult(val colors: List<ExtractedColor>, val isClearlyDominant: Boolean)

/**
 * Extracts dominant colors from a (optionally calibrated, cropped, and white-sample-excluded)
 * photo region, quantizing with Celebi - Wu boxes refined by weighted k-means, the same
 * quantizer Material Color Utilities uses to pick theme colors from a photo.
 */
object ColorExtractor {
    fun extract(
        pixels: PixelSource,
        correction: WhiteBalanceCorrection? = null,
        region: RectRegion? = null,
        exclusion: CircleRegion? = null,
        clusterCount: Int = CalibrationConfig.QUANTIZER_CLUSTER_COUNT,
        minShare: Double = CalibrationConfig.MIN_COLOR_SHARE,
        maxColors: Int = CalibrationConfig.MAX_COLORS,
        dominantMinShare: Double = CalibrationConfig.DOMINANT_MIN_SHARE,
        dominantMinRatioToRunnerUp: Double = CalibrationConfig.DOMINANT_MIN_RATIO_TO_RUNNER_UP,
    ): ColorExtractionResult {
        val bounds = (region ?: RectRegion.fullImage(pixels.width, pixels.height))
            .clampTo(pixels.width, pixels.height)

        val sampled = ArrayList<Int>()
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                if (exclusion != null && exclusion.contains(x, y)) continue
                val argb = pixels.pixelAt(x, y)
                sampled.add(correction?.apply(argb) ?: argb)
            }
        }

        if (sampled.isEmpty()) return ColorExtractionResult(emptyList(), false)

        val quantized = QuantizerCelebi.quantize(sampled.toIntArray(), clusterCount)
        val total = quantized.values.sumOf { it }.toDouble()

        val allColors = quantized.entries
            .map { ExtractedColor(it.key, it.value / total) }
            .sortedByDescending { it.share }

        val isClearlyDominant = allColors.isNotEmpty() &&
            allColors[0].share >= dominantMinShare &&
            (allColors.size == 1 || allColors[0].share >= allColors[1].share * dominantMinRatioToRunnerUp)

        val colors = allColors.filter { it.share >= minShare }.take(maxColors)

        return ColorExtractionResult(colors, isClearlyDominant)
    }
}
