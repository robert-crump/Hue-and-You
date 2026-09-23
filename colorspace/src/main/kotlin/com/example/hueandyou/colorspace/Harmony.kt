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
 * define "equally spaced" differently - implementations added in future slices to support wheels
 * other than [PerceptualWheel] can rotate through an intermediate space instead of HCT hue.
 */
interface ColorWheelStrategy {
    fun rotate(hue: Double, degrees: Double): Double
}

/** Rotates directly in HCT hue space, so equal-degree steps look equally different perceptually. */
object PerceptualWheel : ColorWheelStrategy {
    override fun rotate(hue: Double, degrees: Double): Double {
        val result = (hue + degrees) % 360.0
        return if (result < 0.0) result + 360.0 else result
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

/** Persisted/selectable wheel option, paired with the [ColorWheelStrategy] that implements it. */
enum class HarmonyWheel(val strategy: ColorWheelStrategy) {
    PERCEPTUAL(PerceptualWheel)
}

/** Persisted/selectable balance option, paired with the [BalanceMode] that implements it. */
enum class HarmonyBalance(val mode: BalanceMode) {
    FAITHFUL(FaithfulBalance)
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
