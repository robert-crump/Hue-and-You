package com.example.hueandyou.colorspace

import kotlin.math.abs

/** The relationships [HarmonyEngine] generates suggestions for, relative to a reference color. */
enum class HarmonyRelationship {
    COMPLEMENTARY, SPLIT_COMPLEMENTARY, ANALOGOUS, TRIADIC, TONAL
}

/** One relationship's suggested color(s) for a reference color. */
data class HarmonySuggestion(val relationship: HarmonyRelationship, val colors: List<Int>)

/**
 * Maps a reference hue plus a signed rotation (in degrees) to a resulting hue. Different wheels
 * define "equally spaced" differently - [PerceptualWheel] rotates directly in HCT hue,
 * [TraditionalWheel] and [ScreenWheel] rotate through an intermediate wheel instead.
 */
interface ColorWheelStrategy {
    fun rotate(hue: Double, degrees: Double): Double
}

/** Rotates directly in HCT hue space, so equal-degree steps look equally different perceptually. */
object PerceptualWheel : ColorWheelStrategy {
    override fun rotate(hue: Double, degrees: Double): Double = wrapDegrees(hue + degrees)
}

/**
 * A piecewise-linear map between HCT hue and another hue wheel, built from anchor points where
 * named colors line up on both wheels (e.g. HCT's yellow hue vs. the painter's wheel's yellow at
 * 120 degrees). Anchors must be sorted ascending by their HCT hue and span less than one full
 * turn; both directions wrap at 360.
 */
private class PiecewiseHueMap(hctToOther: List<Pair<Double, Double>>) {
    private val forwardAnchors = closeLoop(hctToOther)
    private val inverseAnchors = closeLoop(hctToOther.map { (hct, other) -> other to hct })

    /** HCT hue -> the other wheel's hue. */
    fun forward(hue: Double): Double = interpolate(hue, forwardAnchors)

    /** The other wheel's hue -> HCT hue. */
    fun inverse(hue: Double): Double = interpolate(hue, inverseAnchors)

    private fun closeLoop(anchors: List<Pair<Double, Double>>): List<Pair<Double, Double>> =
        anchors + (anchors[0].first + 360.0 to anchors[0].second + 360.0)

    private fun interpolate(hue: Double, anchors: List<Pair<Double, Double>>): Double {
        var x = wrapDegrees(hue)
        if (x < anchors.first().first) x += 360.0
        for (i in 0 until anchors.size - 1) {
            val (x0, y0) = anchors[i]
            val (x1, y1) = anchors[i + 1]
            if (x in x0..x1) {
                val t = if (x1 == x0) 0.0 else (x - x0) / (x1 - x0)
                return wrapDegrees(y0 + t * (y1 - y0))
            }
        }
        return wrapDegrees(anchors.last().second)
    }
}

private fun wrapDegrees(degrees: Double): Double {
    val result = degrees % 360.0
    return if (result < 0.0) result + 360.0 else result
}

// Anchor hues, in HCT (from Material Color Utilities), for the sRGB primaries/secondaries that
// give each wheel below its named positions. Computed once from argbToHct and hardcoded as
// starting guesses - e.g. HCT's "yellow" (pure #FFFF00) sits at hue ~111, not the 60 degrees a
// naive RGB-hue reading would suggest.
private val RYB_WHEEL_ANCHORS = listOf(
    27.408 to 0.0,   // red
    52.480 to 60.0,  // orange
    111.051 to 120.0, // yellow
    142.140 to 180.0, // green
    282.788 to 240.0, // blue
    304.537 to 300.0, // violet
)

private val HSV_WHEEL_ANCHORS = listOf(
    27.408 to 0.0,   // red
    111.051 to 60.0, // yellow
    142.140 to 120.0, // green
    196.545 to 180.0, // cyan
    282.788 to 240.0, // blue
    334.635 to 300.0, // magenta
)

/**
 * The traditional painter's wheel (Goethe/Itten, RYB): HCT hue is mapped to a hue on the RYB
 * wheel, rotated there so equal steps match the classic red/yellow/blue-primary color wheel, then
 * mapped back to HCT hue.
 */
object TraditionalWheel : ColorWheelStrategy {
    private val map = PiecewiseHueMap(RYB_WHEEL_ANCHORS)

    override fun rotate(hue: Double, degrees: Double): Double {
        val rybHue = wrapDegrees(map.forward(hue) + degrees)
        return map.inverse(rybHue)
    }
}

/**
 * The screen/paint-program wheel (RGB/HSV): HCT hue is mapped to standard HSV hue, rotated there,
 * then mapped back to HCT hue.
 */
object ScreenWheel : ColorWheelStrategy {
    private val map = PiecewiseHueMap(HSV_WHEEL_ANCHORS)

    override fun rotate(hue: Double, degrees: Double): Double {
        val hsvHue = wrapDegrees(map.forward(hue) + degrees)
        return map.inverse(hsvHue)
    }
}

/** Decides how a suggestion's tone and chroma relate to the reference color's. */
interface BalanceMode {
    fun apply(reference: Hct, hue: Double): Hct
}

/** Keeps the reference color's tone and chroma exactly; only the hue moves. */
object FaithfulBalance : BalanceMode {
    override fun apply(reference: Hct, hue: Double): Hct = Hct(hue, reference.chroma, reference.tone)
}

/** Starting-guess tuning constants for [BalanceMode]s, gathered here so they're easy to retune. */
object HarmonyConfig {
    /** [SoftenedBalance] multiplies the reference color's chroma by this factor. */
    const val SOFTENED_CHROMA_FACTOR = 0.7

    /** [SoftenedBalance] moves tone toward this target tone... */
    const val SOFTENED_TONE_TARGET = 65.0

    /** ...by this fraction of the distance between the reference's own tone and the target. */
    const val SOFTENED_TONE_PULL_FRACTION = 0.3
}

/** Mutes the reference color: lower chroma, tone pulled toward a mid-light target. */
object SoftenedBalance : BalanceMode {
    override fun apply(reference: Hct, hue: Double): Hct {
        val chroma = reference.chroma * HarmonyConfig.SOFTENED_CHROMA_FACTOR
        val tone = reference.tone +
            (HarmonyConfig.SOFTENED_TONE_TARGET - reference.tone) * HarmonyConfig.SOFTENED_TONE_PULL_FRACTION
        return Hct(hue, chroma, tone)
    }
}

/** Persisted/selectable wheel option, paired with the [ColorWheelStrategy] that implements it. */
enum class HarmonyWheel(val strategy: ColorWheelStrategy) {
    TRADITIONAL(TraditionalWheel),
    SCREEN(ScreenWheel),
    PERCEPTUAL(PerceptualWheel),
}

/** Persisted/selectable balance option, paired with the [BalanceMode] that implements it. */
enum class HarmonyBalance(val mode: BalanceMode) {
    FAITHFUL(FaithfulBalance),
    SOFTENED(SoftenedBalance),
}

/**
 * Generates a structured set of harmony relationships for a reference color, given a color wheel
 * strategy and a balance mode. Pure Kotlin, fully unit-testable. Every output color is clamped to
 * the sRGB gamut via [hctToArgb], which resolves HCT through Material Color Utilities' in-gamut
 * solver.
 */
object HarmonyEngine {
    private val TONAL_TONES = listOf(30.0, 50.0, 70.0, 90.0)

    fun generate(referenceArgb: Int, wheel: HarmonyWheel, balance: HarmonyBalance): List<HarmonySuggestion> {
        val reference = argbToHct(referenceArgb)
        val strategy = wheel.strategy
        val mode = balance.mode

        fun colorAt(offsetDegrees: Double): Int =
            hctToArgb(mode.apply(reference, strategy.rotate(reference.hue, offsetDegrees)))

        return listOf(
            HarmonySuggestion(HarmonyRelationship.COMPLEMENTARY, listOf(colorAt(180.0))),
            HarmonySuggestion(HarmonyRelationship.SPLIT_COMPLEMENTARY, listOf(colorAt(150.0), colorAt(210.0))),
            HarmonySuggestion(HarmonyRelationship.ANALOGOUS, listOf(colorAt(-30.0), colorAt(30.0))),
            HarmonySuggestion(HarmonyRelationship.TRIADIC, listOf(colorAt(120.0), colorAt(240.0))),
            HarmonySuggestion(HarmonyRelationship.TONAL, tonalColors(reference)),
        )
    }

    private fun tonalColors(reference: Hct): List<Int> {
        val closest = TONAL_TONES.minBy { abs(it - reference.tone) }
        return TONAL_TONES.filter { it != closest }
            .map { tone -> hctToArgb(Hct(reference.hue, reference.chroma, tone)) }
    }
}
