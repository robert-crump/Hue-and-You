package com.example.hueandyou.ui.outfits

import com.example.hueandyou.colorspace.ClothingVerdict
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.OutfitHarmony
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutfitRatingTest {

    private val autumn = profile(1L, "Autumn", best = RED, avoid = GREEN)

    @Test
    fun suitsYou_countsBestItemsAndCallsOutAvoidOnesByPosition() {
        val rating = rateOutfit(listOf(RED, GREEN, BLUE, RED), autumn, HarmonyWheel.PERCEPTUAL)

        assertEquals(SuitsYouSummary(itemCount = 4, bestCount = 2, avoidIndices = listOf(1)), rating?.suitsYou)
        assertEquals(ClothingVerdict.AVOID, rating?.suitsYou?.overall)
    }

    @Test
    fun overall_isYesForAMajorityOfBestItemsWithNoneToAvoid_elseNeither() {
        assertEquals(ClothingVerdict.YES, SuitsYouSummary(3, 2, emptyList()).overall)
        assertEquals(ClothingVerdict.NEITHER, SuitsYouSummary(4, 2, emptyList()).overall)
        assertEquals(ClothingVerdict.AVOID, SuitsYouSummary(3, 2, listOf(2)).overall)
    }

    @Test
    fun withoutAProfile_onlyTheHarmonyVerdictIsGiven() {
        val rating = rateOutfit(listOf(RED, BLUE), profile = null, HarmonyWheel.PERCEPTUAL)

        assertNull(rating?.suitsYou)
        assertEquals(OutfitHarmony.classify(listOf(RED, BLUE), HarmonyWheel.PERCEPTUAL), rating?.harmony)
    }

    @Test
    fun harmony_usesTheGivenWheel() {
        HarmonyWheel.entries.forEach { wheel ->
            assertEquals(
                OutfitHarmony.classify(listOf(RED, GREEN, BLUE), wheel),
                rateOutfit(listOf(RED, GREEN, BLUE), autumn, wheel)?.harmony,
            )
        }
    }

    @Test
    fun tooFewOrTooManyItems_areNotRated() {
        assertNull(rateOutfit(emptyList(), autumn, HarmonyWheel.PERCEPTUAL))
        assertNull(rateOutfit(listOf(RED), autumn, HarmonyWheel.PERCEPTUAL))
        assertNull(rateOutfit(List(9) { RED }, autumn, HarmonyWheel.PERCEPTUAL))
        assertEquals(8, rateOutfit(List(8) { RED }, autumn, HarmonyWheel.PERCEPTUAL)?.suitsYou?.itemCount)
    }
}
