package com.example.hueandyou.colorspace

/** One candidate color's distance from the measured color. */
data class ColorMatch(val argb: Int, val deltaE: Double)

/**
 * Result of scoring one measured color against a profile's Best/Avoid lists. Either side is
 * null when that list was empty.
 */
data class PaletteScore(
    val nearestBest: ColorMatch?,
    val nearestAvoid: ColorMatch?,
    val closerToAvoid: Boolean,
)

/**
 * Scores a measured garment color against a profile's Best and Avoid color lists, in CIELAB
 * via ΔE00 ([ciede2000]). Pure Kotlin so it's fully unit-testable without a device/emulator.
 */
object PaletteScorer {
    fun score(measuredArgb: Int, bestColors: List<Int>, avoidColors: List<Int>): PaletteScore {
        val measuredLab = argbToLab(measuredArgb)
        val nearestBest = nearestMatch(measuredLab, bestColors)
        val nearestAvoid = nearestMatch(measuredLab, avoidColors)
        val closerToAvoid = nearestBest != null && nearestAvoid != null &&
            nearestAvoid.deltaE < nearestBest.deltaE
        return PaletteScore(nearestBest, nearestAvoid, closerToAvoid)
    }

    private fun nearestMatch(measuredLab: Lab, candidates: List<Int>): ColorMatch? =
        candidates.minByOrNull { ciede2000(measuredLab, argbToLab(it)) }?.let { argb ->
            ColorMatch(argb, ciede2000(measuredLab, argbToLab(argb)))
        }
}
