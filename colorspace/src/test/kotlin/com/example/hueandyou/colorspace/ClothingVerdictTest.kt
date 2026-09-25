package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Test

class ClothingVerdictTest {

    private val bestArgb = 0xFF112233.toInt()
    private val avoidArgb = 0xFF445566.toInt()

    private fun scoreOf(bestDeltaE: Double?, avoidDeltaE: Double?) = PaletteScore(
        nearestBest = bestDeltaE?.let { ColorMatch(bestArgb, it) },
        nearestAvoid = avoidDeltaE?.let { ColorMatch(avoidArgb, it) },
        closerToAvoid = bestDeltaE != null && avoidDeltaE != null && avoidDeltaE < bestDeltaE,
    )

    @Test
    fun yesWhenBestIsWithinThresholdAndCloserThanAvoid() {
        assertEquals(ClothingVerdict.YES, ClothingVerdict.forScore(scoreOf(bestDeltaE = 5.0, avoidDeltaE = 20.0)))
    }

    @Test
    fun avoidWhenAvoidIsWithinThresholdAndCloserThanBest() {
        assertEquals(ClothingVerdict.AVOID, ClothingVerdict.forScore(scoreOf(bestDeltaE = 20.0, avoidDeltaE = 5.0)))
    }

    @Test
    fun avoidWinsEvenWhenBestIsFartherThanThresholdAsLongAsAvoidIsCloserAndWithin() {
        assertEquals(ClothingVerdict.AVOID, ClothingVerdict.forScore(scoreOf(bestDeltaE = 30.0, avoidDeltaE = 5.0)))
    }

    @Test
    fun neitherWhenBothAreFartherThanThreshold() {
        assertEquals(ClothingVerdict.NEITHER, ClothingVerdict.forScore(scoreOf(bestDeltaE = 13.0, avoidDeltaE = 14.0)))
    }

    @Test
    fun neitherOnATieBetweenBestAndAvoid() {
        assertEquals(ClothingVerdict.NEITHER, ClothingVerdict.forScore(scoreOf(bestDeltaE = 5.0, avoidDeltaE = 5.0)))
    }

    @Test
    fun exactlyAtTheThresholdCountsAsWithin() {
        assertEquals(
            ClothingVerdict.YES,
            ClothingVerdict.forScore(scoreOf(bestDeltaE = ClothingVerdict.VERDICT_MAX_DELTA_E, avoidDeltaE = null))
        )
    }

    @Test
    fun justOverTheThresholdIsNeither() {
        assertEquals(
            ClothingVerdict.NEITHER,
            ClothingVerdict.forScore(
                scoreOf(bestDeltaE = ClothingVerdict.VERDICT_MAX_DELTA_E + 0.0001, avoidDeltaE = null)
            )
        )
    }

    @Test
    fun emptyBestListCanOnlyYieldAvoidOrNeither() {
        assertEquals(ClothingVerdict.AVOID, ClothingVerdict.forScore(scoreOf(bestDeltaE = null, avoidDeltaE = 5.0)))
        assertEquals(ClothingVerdict.NEITHER, ClothingVerdict.forScore(scoreOf(bestDeltaE = null, avoidDeltaE = 20.0)))
        assertEquals(ClothingVerdict.NEITHER, ClothingVerdict.forScore(scoreOf(bestDeltaE = null, avoidDeltaE = null)))
    }

    @Test
    fun emptyAvoidListCanOnlyYieldYesOrNeither() {
        assertEquals(ClothingVerdict.YES, ClothingVerdict.forScore(scoreOf(bestDeltaE = 5.0, avoidDeltaE = null)))
        assertEquals(ClothingVerdict.NEITHER, ClothingVerdict.forScore(scoreOf(bestDeltaE = 20.0, avoidDeltaE = null)))
    }
}
