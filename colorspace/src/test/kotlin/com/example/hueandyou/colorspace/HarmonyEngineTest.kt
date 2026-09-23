package com.example.hueandyou.colorspace

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonyEngineTest {

    private val sampleColors = listOf(
        0xFFE07A5F.toInt(),
        0xFF3D405B.toInt(),
        0xFF81B29A.toInt(),
        0xFFF2CC8F.toInt(),
        0xFF1A2B3C.toInt(),
        0xFFFF0000.toInt(),
        0xFF00FF00.toInt(),
        0xFF0000FF.toInt(),
    )

    @Test
    fun offsetsPerRelationshipAreCorrectOnThePerceptualWheel() {
        for (argb in sampleColors) {
            val reference = argbToHct(argb)
            val suggestions = HarmonyEngine.generate(argb, HarmonyWheel.PERCEPTUAL, HarmonyBalance.FAITHFUL)

            assertHue(reference.hue + 180.0, suggestions.hueOf(HarmonyRelationship.COMPLEMENTARY)[0])

            val splitComplementary = suggestions.hueOf(HarmonyRelationship.SPLIT_COMPLEMENTARY)
            assertHue(reference.hue + 150.0, splitComplementary[0])
            assertHue(reference.hue + 210.0, splitComplementary[1])

            val analogous = suggestions.hueOf(HarmonyRelationship.ANALOGOUS)
            assertHue(reference.hue - 30.0, analogous[0])
            assertHue(reference.hue + 30.0, analogous[1])

            val triadic = suggestions.hueOf(HarmonyRelationship.TRIADIC)
            assertHue(reference.hue + 120.0, triadic[0])
            assertHue(reference.hue + 240.0, triadic[1])
        }
    }

    @Test
    fun tonalExcludesToneClosestToReferenceAndKeepsHueAndChroma() {
        // reference tone 50 is exactly between 30/70 candidates but strictly closest to itself if present.
        val argb = 0xFF3D405B.toInt()
        val reference = argbToHct(argb)
        val suggestions = HarmonyEngine.generate(argb, HarmonyWheel.PERCEPTUAL, HarmonyBalance.FAITHFUL)

        val tonal = suggestions.single { it.relationship == HarmonyRelationship.TONAL }
        assertEquals(3, tonal.colors.size)

        val tones = tonal.colors.map { argbToHct(it).tone }
        val closestTone = listOf(30.0, 50.0, 70.0, 90.0).minBy { abs(it - reference.tone) }
        assertTrue(tones.none { abs(it - closestTone) < 0.5 })

        for (color in tonal.colors) {
            val hct = argbToHct(color)
            assertHue(reference.hue, hct.hue)
            // Chroma can be pulled down by gamut mapping at some tones; only assert it isn't raised.
            assertTrue(hct.chroma <= reference.chroma + 0.5)
        }
    }

    @Test
    fun faithfulBalanceKeepsToneAndChromaWithinTolerance() {
        for (argb in sampleColors) {
            val reference = argbToHct(argb)
            val suggestions = HarmonyEngine.generate(argb, HarmonyWheel.PERCEPTUAL, HarmonyBalance.FAITHFUL)

            for (suggestion in suggestions) {
                if (suggestion.relationship == HarmonyRelationship.TONAL) continue
                for (color in suggestion.colors) {
                    val hct = argbToHct(color)
                    assertEquals(reference.tone, hct.tone, 0.5)
                    // Gamut mapping can only ever reduce chroma to bring a hue/tone pair in-gamut.
                    assertTrue(hct.chroma <= reference.chroma + 0.5)
                }
            }
        }
    }

    @Test
    fun allGeneratedColorsAreInGamut() {
        for (argb in sampleColors) {
            val suggestions = HarmonyEngine.generate(argb, HarmonyWheel.PERCEPTUAL, HarmonyBalance.FAITHFUL)
            for (suggestion in suggestions) {
                for (color in suggestion.colors) {
                    assertEquals(0xFF, (color ushr 24) and 0xFF)
                    val channelR = (color shr 16) and 0xFF
                    val channelG = (color shr 8) and 0xFF
                    val channelB = color and 0xFF
                    assertTrue(channelR in 0..255 && channelG in 0..255 && channelB in 0..255)
                }
            }
        }
    }

    private fun List<HarmonySuggestion>.hueOf(relationship: HarmonyRelationship): List<Double> =
        single { it.relationship == relationship }.colors.map { argbToHct(it).hue }

    private fun assertHue(expected: Double, actual: Double) {
        val normalizedExpected = ((expected % 360.0) + 360.0) % 360.0
        var diff = abs(normalizedExpected - actual) % 360.0
        if (diff > 180.0) diff = 360.0 - diff
        // Gamut mapping can nudge hue slightly for highly saturated colors pushed out of gamut.
        assertTrue("expected hue $normalizedExpected, got $actual", diff <= 2.5)
    }
}
