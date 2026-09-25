package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorExtractorSelectAlternativesTest {

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val yellow = 0xFFFFFF00.toInt()
    private val purple = 0xFF800080.toInt()

    @Test
    fun excludesCandidatesNearlyIdenticalToTheCurrentColor() {
        val nearlyRed = 0xFFFF0002.toInt()

        val alternatives = ColorExtractor.selectAlternatives(
            candidates = listOf(nearlyRed, green, blue),
            currentArgb = red,
        )

        assertFalse(alternatives.contains(nearlyRed))
        assertEquals(listOf(green, blue), alternatives)
    }

    @Test
    fun excludesCandidatesNearlyIdenticalToAnEarlierAlternative() {
        val nearlyGreen = 0xFF00FF02.toInt()

        val alternatives = ColorExtractor.selectAlternatives(
            candidates = listOf(green, nearlyGreen, blue),
            currentArgb = red,
        )

        assertEquals(listOf(green, blue), alternatives)
    }

    @Test
    fun capsAtMaxAlternatives() {
        val alternatives = ColorExtractor.selectAlternatives(
            candidates = listOf(green, blue, yellow, purple, 0xFF444444.toInt()),
            currentArgb = red,
            maxAlternatives = 2,
        )

        assertEquals(listOf(green, blue), alternatives)
    }

    @Test
    fun preservesCandidateOrder() {
        val alternatives = ColorExtractor.selectAlternatives(
            candidates = listOf(purple, blue, green),
            currentArgb = red,
            maxAlternatives = 3,
        )

        assertEquals(listOf(purple, blue, green), alternatives)
    }

    @Test
    fun emptyCandidatesYieldsNoAlternatives() {
        val alternatives = ColorExtractor.selectAlternatives(candidates = emptyList(), currentArgb = red)

        assertTrue(alternatives.isEmpty())
    }

    @Test
    fun selectTopColors_returnsTopCandidateFollowedByTwoDistinctOnes() {
        val nearlyRed = 0xFFFF0002.toInt()

        val top = ColorExtractor.selectTopColors(listOf(red, nearlyRed, green, blue, yellow))

        assertEquals(listOf(red, green, blue), top)
    }

    @Test
    fun selectTopColors_emptyCandidatesYieldsNothing() {
        assertTrue(ColorExtractor.selectTopColors(emptyList()).isEmpty())
    }
}
