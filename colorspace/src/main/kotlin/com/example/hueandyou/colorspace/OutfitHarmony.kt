package com.example.hueandyou.colorspace

/** How well a set of colors works together, see [OutfitHarmony.classify]. */
enum class OutfitHarmonyLevel { WORKS, BORDERLINE, CLASHES }

/** The hue scheme a set of colors fits (or nearly fits). */
enum class OutfitScheme { NEUTRAL, TONAL, ANALOGOUS, COMPLEMENTARY }

/**
 * - [OutfitHarmonyLevel.WORKS]: [scheme] is the scheme the colors fit.
 * - [OutfitHarmonyLevel.BORDERLINE]: [scheme] is the nearest scheme, just outside its tolerance.
 * - [OutfitHarmonyLevel.CLASHES]: [oddOneOutIndex] is the input index whose removal makes the rest
 *   work, with [scheme] the scheme they then fit; both null if no single color can be blamed.
 */
data class OutfitHarmonyResult(
    val level: OutfitHarmonyLevel,
    val scheme: OutfitScheme?,
    val oddOneOutIndex: Int? = null,
)

/** Tolerances for [OutfitHarmony], in degrees on the chosen wheel unless noted. */
object OutfitHarmonyConfig {
    /** Below this HCT chroma a color is neutral and ignored (same cutoff as the profile preview). */
    const val NEUTRAL_CHROMA_THRESHOLD = 16.0

    /** Tonal: all hues fit in an arc this wide. */
    const val TONAL_MAX_SPAN = 15.0

    /** Analogous: all hues fit in an arc this wide. */
    const val ANALOGOUS_MAX_SPAN = 60.0

    /** Complementary: each of the two clusters fits in an arc this wide... */
    const val COMPLEMENTARY_CLUSTER_MAX_SPAN = 30.0

    /** ...and their centers are 180 degrees apart, give or take this much. */
    const val COMPLEMENTARY_OPPOSITION_TOLERANCE = 25.0

    /** A set this far (or less) outside its nearest scheme's tolerance is borderline, not a clash. */
    const val BORDERLINE_MARGIN = 15.0

    const val MIN_COLORS = 2
    const val MAX_COLORS = 8
}

/**
 * Answers "do these colors work together?" for an outfit's 2-8 colors. Hues are compared on the
 * user's [HarmonyWheel], so the verdict agrees with the Objects harmony suggestions. Tone and
 * contrast are not considered.
 */
object OutfitHarmony {

    fun classify(colorsArgb: List<Int>, wheel: HarmonyWheel): OutfitHarmonyResult {
        require(colorsArgb.size in OutfitHarmonyConfig.MIN_COLORS..OutfitHarmonyConfig.MAX_COLORS) {
            "Expected ${OutfitHarmonyConfig.MIN_COLORS}-${OutfitHarmonyConfig.MAX_COLORS} colors, got ${colorsArgb.size}"
        }
        // Input index -> hue on the wheel, for chromatic colors only.
        val hues = colorsArgb.withIndex()
            .map { (index, argb) -> index to argbToHct(argb) }
            .filter { (_, hct) -> hct.chroma >= OutfitHarmonyConfig.NEUTRAL_CHROMA_THRESHOLD }
            .map { (index, hct) -> index to wheel.strategy.wheelHue(hct.hue) }

        val fit = bestFit(hues.map { it.second })
        if (fit.deviation <= 0.0) return OutfitHarmonyResult(OutfitHarmonyLevel.WORKS, fit.scheme)
        if (fit.deviation <= OutfitHarmonyConfig.BORDERLINE_MARGIN) {
            return OutfitHarmonyResult(OutfitHarmonyLevel.BORDERLINE, fit.scheme)
        }

        val culprits = hues.mapNotNull { (index, _) ->
            val rest = bestFit(hues.filter { it.first != index }.map { it.second })
            if (rest.deviation <= 0.0) index to rest.scheme else null
        }
        val culprit = culprits.singleOrNull()
        return OutfitHarmonyResult(OutfitHarmonyLevel.CLASHES, culprit?.second, culprit?.first)
    }

    /** A scheme and how far outside its tolerance the hues are (zero or negative means inside). */
    private data class Fit(val scheme: OutfitScheme, val deviation: Double)

    /** The tightest scheme the hues fit (Tonal, then Analogous, then Complementary), else the nearest. */
    private fun bestFit(hues: List<Double>): Fit {
        if (hues.size <= 1) return Fit(OutfitScheme.NEUTRAL, 0.0)
        val sorted = hues.sorted()
        // gaps[i] is the gap going from sorted[i] to the next hue round the wheel.
        val gaps = sorted.indices.map { i ->
            if (i == sorted.lastIndex) sorted[0] + 360.0 - sorted[i] else sorted[i + 1] - sorted[i]
        }
        val span = 360.0 - gaps.max()
        val fits = listOf(
            Fit(OutfitScheme.TONAL, span - OutfitHarmonyConfig.TONAL_MAX_SPAN),
            Fit(OutfitScheme.ANALOGOUS, span - OutfitHarmonyConfig.ANALOGOUS_MAX_SPAN),
            Fit(OutfitScheme.COMPLEMENTARY, complementaryDeviation(sorted, gaps)),
        )
        return fits.firstOrNull { it.deviation <= 0.0 } ?: fits.minBy { it.deviation }
    }

    /**
     * Splits the hues into two clusters at the two largest gaps, then measures how far the wider
     * cluster and the distance between cluster centers are outside the Complementary tolerances.
     */
    private fun complementaryDeviation(sorted: List<Double>, gaps: List<Double>): Double {
        val (a, b) = gaps.indices.sortedByDescending { gaps[it] }.take(2).sorted()
        // Cluster 1 runs from sorted[a + 1] to sorted[b]; cluster 2 from sorted[b + 1] round to sorted[a].
        val span1 = (a + 1 until b).sumOf { gaps[it] }
        val span2 = 360.0 - gaps[a] - gaps[b] - span1
        val center1 = sorted[a + 1] + span1 / 2.0
        val center2 = sorted[(b + 1) % sorted.size] + span2 / 2.0
        val opposition = hueDistance(center1, center2)
        return maxOf(
            maxOf(span1, span2) - OutfitHarmonyConfig.COMPLEMENTARY_CLUSTER_MAX_SPAN,
            (180.0 - opposition) - OutfitHarmonyConfig.COMPLEMENTARY_OPPOSITION_TOLERANCE,
        )
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = ((a - b) % 360.0 + 360.0) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}
