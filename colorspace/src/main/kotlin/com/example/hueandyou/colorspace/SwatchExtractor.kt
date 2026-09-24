package com.example.hueandyou.colorspace

import kotlin.math.sqrt

/**
 * Extracts the distinct flat colors from a marked region of a palette screenshot (a grid of
 * solid swatches). Unlike [ColorExtractor]'s quantizer, which silently merges or drops colors
 * once a palette has more than a couple of dozen swatches, this counts exact pixel colors and
 * greedily groups them by perceptual distance, so a swatch the region only partly covers still
 * yields its own color and anti-aliased edge pixels fold into the nearest swatch.
 */
object SwatchExtractor {
    fun extract(
        pixels: PixelSource,
        region: RectRegion? = null,
        minShare: Double = CalibrationConfig.PALETTE_IMPORT_MIN_COLOR_SHARE,
        maxColors: Int = CalibrationConfig.PALETTE_IMPORT_MAX_COLORS,
        mergeDistance: Double = CalibrationConfig.PALETTE_IMPORT_MERGE_DISTANCE,
    ): List<ExtractedColor> {
        val bounds = (region ?: RectRegion.fullImage(pixels.width, pixels.height))
            .clampTo(pixels.width, pixels.height)

        val counts = HashMap<Int, Int>()
        var total = 0
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                val opaque = pixels.pixelAt(x, y) or OPAQUE_ALPHA
                counts[opaque] = (counts[opaque] ?: 0) + 1
                total++
            }
        }
        if (total == 0) return emptyList()

        // Most frequent first, so each group is represented by its dominant exact color.
        val candidates = counts.entries
            .sortedByDescending { it.value }
            .map { Candidate(it.key, it.value, argbToLab(it.key)) }

        val groups = ArrayList<Group>()
        for (candidate in candidates) {
            val group = groups.firstOrNull { distance(it.lab, candidate.lab) <= mergeDistance }
            if (group != null) {
                group.count += candidate.count
            } else {
                groups.add(Group(candidate.argb, candidate.lab, candidate.count))
            }
        }

        return groups
            .sortedByDescending { it.count }
            .map { ExtractedColor(it.argb, it.count.toDouble() / total) }
            .filter { it.share >= minShare }
            .take(maxColors)
    }

    private fun distance(a: Lab, b: Lab): Double {
        val dl = a.l - b.l
        val da = a.a - b.a
        val db = a.b - b.b
        return sqrt(dl * dl + da * da + db * db)
    }

    private const val OPAQUE_ALPHA = 0xFF shl 24

    private class Candidate(val argb: Int, val count: Int, val lab: Lab)

    private class Group(val argb: Int, val lab: Lab, var count: Int)
}
