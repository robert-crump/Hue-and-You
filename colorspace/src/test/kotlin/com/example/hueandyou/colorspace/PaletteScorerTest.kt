package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteScorerTest {

    @Test
    fun bandBoundaries() {
        assertEquals(ColorMatchBand.MATCH, ColorMatchBand.forDeltaE(0.0))
        assertEquals(ColorMatchBand.MATCH, ColorMatchBand.forDeltaE(3.0))
        assertEquals(ColorMatchBand.CLOSE, ColorMatchBand.forDeltaE(3.0001))
        assertEquals(ColorMatchBand.CLOSE, ColorMatchBand.forDeltaE(8.0))
        assertEquals(ColorMatchBand.RELATED, ColorMatchBand.forDeltaE(8.0001))
        assertEquals(ColorMatchBand.RELATED, ColorMatchBand.forDeltaE(15.0))
        assertEquals(ColorMatchBand.FAR, ColorMatchBand.forDeltaE(15.0001))
        assertEquals(ColorMatchBand.FAR, ColorMatchBand.forDeltaE(100.0))
    }

    @Test
    fun nearestColorIsSelectedFromSeveralCandidates() {
        val measured = 0xFF3050C0.toInt()
        val near = 0xFF3050C5.toInt()
        val far = 0xFFFFAA00.toInt()

        val score = PaletteScorer.score(measured, bestColors = listOf(far, near), avoidColors = emptyList())

        assertEquals(near, score.nearestBest?.argb)
    }

    @Test
    fun closerToAvoidFlagIsTrueWhenAvoidIsNearer() {
        val measured = 0xFF202020.toInt()
        val closeAvoid = 0xFF212121.toInt()
        val farBest = 0xFFEEEEEE.toInt()

        val score = PaletteScorer.score(measured, bestColors = listOf(farBest), avoidColors = listOf(closeAvoid))

        assertTrue(score.closerToAvoid)
    }

    @Test
    fun closerToAvoidFlagIsFalseWhenBestIsNearer() {
        val measured = 0xFF202020.toInt()
        val closeBest = 0xFF212121.toInt()
        val farAvoid = 0xFFEEEEEE.toInt()

        val score = PaletteScorer.score(measured, bestColors = listOf(closeBest), avoidColors = listOf(farAvoid))

        assertFalse(score.closerToAvoid)
    }

    @Test
    fun emptyBestListOmitsNearestBest() {
        val score = PaletteScorer.score(
            measuredArgb = 0xFF202020.toInt(),
            bestColors = emptyList(),
            avoidColors = listOf(0xFFEEEEEE.toInt()),
        )

        assertNull(score.nearestBest)
        assertTrue(score.nearestAvoid != null)
        assertFalse(score.closerToAvoid)
    }

    @Test
    fun emptyAvoidListOmitsNearestAvoid() {
        val score = PaletteScorer.score(
            measuredArgb = 0xFF202020.toInt(),
            bestColors = listOf(0xFF212121.toInt()),
            avoidColors = emptyList(),
        )

        assertTrue(score.nearestBest != null)
        assertNull(score.nearestAvoid)
        assertFalse(score.closerToAvoid)
    }

    @Test
    fun bothListsEmptyYieldsNoMatchesAndNoFlag() {
        val score = PaletteScorer.score(0xFF202020.toInt(), emptyList(), emptyList())

        assertNull(score.nearestBest)
        assertNull(score.nearestAvoid)
        assertFalse(score.closerToAvoid)
    }
}
