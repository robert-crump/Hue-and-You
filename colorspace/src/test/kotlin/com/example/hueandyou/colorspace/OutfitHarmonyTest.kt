package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Test

class OutfitHarmonyTest {

    private val gray = 0xFF808080.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private val red = 0xFFFF0000.toInt()
    private val yellow = 0xFFFFFF00.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    /** A clearly chromatic color at this HCT hue. */
    private fun hue(degrees: Double, chroma: Double = 36.0): Int = hctToArgb(Hct(degrees, chroma, 60.0))

    private fun classify(vararg colors: Int, wheel: HarmonyWheel = HarmonyWheel.PERCEPTUAL) =
        OutfitHarmony.classify(colors.toList(), wheel)

    private fun works(scheme: OutfitScheme) = OutfitHarmonyResult(OutfitHarmonyLevel.WORKS, scheme)
    private fun borderline(scheme: OutfitScheme) = OutfitHarmonyResult(OutfitHarmonyLevel.BORDERLINE, scheme)

    @Test
    fun allNeutralWorksAsNeutral() {
        assertEquals(works(OutfitScheme.NEUTRAL), classify(gray, white, black, hue(30.0, chroma = 8.0)))
    }

    @Test
    fun singleChromaticColorAmongNeutralsWorksAsNeutral() {
        assertEquals(works(OutfitScheme.NEUTRAL), classify(gray, hue(30.0), black))
    }

    @Test
    fun neutralsAreIgnored() {
        // The gray would sit at any hue if it counted; the two blues are tonal on their own.
        assertEquals(works(OutfitScheme.TONAL), classify(hue(250.0), gray, hue(258.0), white))
    }

    @Test
    fun huesWithinFifteenDegreesAreTonal() {
        assertEquals(works(OutfitScheme.TONAL), classify(hue(200.0), hue(208.0), hue(212.0)))
    }

    @Test
    fun tonalWrapsAroundZero() {
        assertEquals(works(OutfitScheme.TONAL), classify(hue(355.0), hue(5.0)))
    }

    @Test
    fun huesWithinSixtyDegreesAreAnalogous() {
        assertEquals(works(OutfitScheme.ANALOGOUS), classify(hue(100.0), hue(130.0), hue(150.0)))
    }

    @Test
    fun analogousWrapsAroundZero() {
        assertEquals(works(OutfitScheme.ANALOGOUS), classify(hue(330.0), hue(350.0), hue(20.0)))
    }

    @Test
    fun justOutsideTonalIsStillAnalogous() {
        // A near-tonal set always fits Analogous, so Tonal itself is never the borderline scheme.
        assertEquals(works(OutfitScheme.ANALOGOUS), classify(hue(200.0), hue(220.0)))
    }

    @Test
    fun twoOppositeClustersAreComplementary() {
        assertEquals(
            works(OutfitScheme.COMPLEMENTARY),
            classify(hue(40.0), hue(50.0), hue(220.0), hue(232.0)),
        )
    }

    @Test
    fun complementaryWrapsAroundZero() {
        assertEquals(
            works(OutfitScheme.COMPLEMENTARY),
            classify(hue(355.0), hue(8.0), hue(175.0), hue(185.0)),
        )
    }

    @Test
    fun slightlyTooWideArcIsBorderlineAnalogous() {
        assertEquals(borderline(OutfitScheme.ANALOGOUS), classify(hue(100.0), hue(140.0), hue(170.0)))
    }

    @Test
    fun slightlyShortOfOppositeIsBorderlineComplementary() {
        assertEquals(borderline(OutfitScheme.COMPLEMENTARY), classify(hue(0.0), hue(145.0)))
    }

    @Test
    fun slightlyTooWideClusterIsBorderlineComplementary() {
        assertEquals(borderline(OutfitScheme.COMPLEMENTARY), classify(hue(30.0), hue(70.0), hue(230.0)))
    }

    @Test
    fun clashNamesTheOddOneOut() {
        // Without the green-yellow at index 2, the two blues are tonal.
        assertEquals(
            OutfitHarmonyResult(OutfitHarmonyLevel.CLASHES, OutfitScheme.TONAL, oddOneOutIndex = 2),
            classify(gray, hue(200.0), hue(100.0), hue(210.0)),
        )
    }

    @Test
    fun clashWithoutSingleCulpritHasNoOddOneOut() {
        // An even triad: dropping any one still leaves two colors 120 degrees apart.
        assertEquals(
            OutfitHarmonyResult(OutfitHarmonyLevel.CLASHES, scheme = null, oddOneOutIndex = null),
            classify(hue(0.0), hue(120.0), hue(240.0)),
        )
    }

    @Test
    fun twoClashingColorsHaveNoOddOneOut() {
        // Dropping either leaves a single color, so neither is to blame more than the other.
        assertEquals(
            OutfitHarmonyResult(OutfitHarmonyLevel.CLASHES, scheme = null, oddOneOutIndex = null),
            classify(hue(0.0), hue(100.0)),
        )
    }

    @Test
    fun yellowAndBlueAreComplementaryOnScreenWheelButClashOnTraditional() {
        assertEquals(works(OutfitScheme.COMPLEMENTARY), classify(yellow, blue, wheel = HarmonyWheel.SCREEN))
        assertEquals(works(OutfitScheme.COMPLEMENTARY), classify(yellow, blue, wheel = HarmonyWheel.PERCEPTUAL))
        assertEquals(OutfitHarmonyLevel.CLASHES, classify(yellow, blue, wheel = HarmonyWheel.TRADITIONAL).level)
    }

    @Test
    fun redAndGreenAreComplementaryOnTraditionalWheelButClashOnScreen() {
        assertEquals(works(OutfitScheme.COMPLEMENTARY), classify(red, green, wheel = HarmonyWheel.TRADITIONAL))
        assertEquals(OutfitHarmonyLevel.CLASHES, classify(red, green, wheel = HarmonyWheel.SCREEN).level)
    }

    @Test
    fun traditionalWheelStretchesRedToOrange() {
        // 14 perceptual degrees apart, but the painter's wheel spreads red-orange over 60 degrees.
        val colors = intArrayOf(hue(30.0), hue(44.0))
        assertEquals(works(OutfitScheme.TONAL), classify(*colors, wheel = HarmonyWheel.PERCEPTUAL))
        assertEquals(works(OutfitScheme.ANALOGOUS), classify(*colors, wheel = HarmonyWheel.TRADITIONAL))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFewerThanTwoColors() {
        OutfitHarmony.classify(listOf(red), HarmonyWheel.PERCEPTUAL)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanEightColors() {
        OutfitHarmony.classify(List(9) { red }, HarmonyWheel.PERCEPTUAL)
    }
}
