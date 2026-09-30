package com.example.hueandyou.ui.outfits

import com.example.hueandyou.colorspace.Hct
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.hctToArgb
import com.example.hueandyou.data.history.ClothingCategory.BELT
import com.example.hueandyou.data.history.ClothingCategory.BOTTOM
import com.example.hueandyou.data.history.ClothingCategory.ONE_PIECE
import com.example.hueandyou.data.history.ClothingCategory.OUTERWEAR
import com.example.hueandyou.data.history.ClothingCategory.SHOES
import com.example.hueandyou.data.history.ClothingCategory.TOP
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.profile.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutfitSuggestionTest {

    private val gray = 0xFF808080.toInt()

    /** A clearly chromatic color at this HCT hue. */
    private fun hue(degrees: Double): Int = hctToArgb(Hct(degrees, 36.0, 60.0))

    private val anchor = clothing(1L, hue(200.0), category = TOP)

    private fun suggest(
        outfit: List<HistoryEntry>,
        clothes: List<HistoryEntry>,
        profile: Profile? = null,
        includeNotOwned: Boolean = false,
    ) = suggestOutfitItems(outfit, clothes, profile, HarmonyWheel.PERCEPTUAL, includeNotOwned)

    private fun OutfitSuggestion.ids() = (this as OutfitSuggestion.Candidates).items.map { it.id }

    // --- Slot selection ---

    @Test
    fun aTopNeedsABottom_andABottomNeedsATop() {
        assertEquals(BOTTOM, nextOutfitSlot(listOf(TOP)))
        assertEquals(TOP, nextOutfitSlot(listOf(BOTTOM, SHOES)))
    }

    @Test
    fun withNoTopBottomOrOnePiece_theTopComesFirst() {
        assertEquals(TOP, nextOutfitSlot(listOf(SHOES, OUTERWEAR)))
        assertEquals(TOP, nextOutfitSlot(emptyList()))
    }

    @Test
    fun uncategorizedItemsFillNoSlot() {
        assertEquals(TOP, nextOutfitSlot(listOf(null, null)))
        assertEquals(BOTTOM, nextOutfitSlot(listOf(TOP, null)))
    }

    @Test
    fun onceTheRequiredSlotsAreFilled_optionalOnesFollowInOrder() {
        assertEquals(OUTERWEAR, nextOutfitSlot(listOf(TOP, BOTTOM)))
        assertEquals(OUTERWEAR, nextOutfitSlot(listOf(ONE_PIECE)))
        assertEquals(SHOES, nextOutfitSlot(listOf(ONE_PIECE, OUTERWEAR)))
        assertEquals(BELT, nextOutfitSlot(listOf(TOP, BOTTOM, OUTERWEAR, SHOES)))
        assertEquals(SHOES, nextOutfitSlot(listOf(TOP, BELT, OUTERWEAR, BOTTOM)))
    }

    @Test
    fun everySlotFilled_isNoSlot() {
        assertNull(nextOutfitSlot(listOf(TOP, BOTTOM, OUTERWEAR, SHOES, BELT)))
        assertNull(nextOutfitSlot(listOf(ONE_PIECE, OUTERWEAR, SHOES, BELT)))
    }

    @Test
    fun anEmptyOutfit_needsAnAnchor() {
        assertEquals(OutfitSuggestion.NeedsAnchor, suggest(emptyList(), listOf(anchor)))
    }

    @Test
    fun aCompleteOrFullOutfit_hasNothingToSuggest() {
        val complete = listOf(TOP, BOTTOM, OUTERWEAR, SHOES, BELT).mapIndexed { i, c -> clothing(i + 1L, gray, category = c) }
        assertEquals(OutfitSuggestion.Complete, suggest(complete, listOf(clothing(9L, gray, category = TOP))))

        val full = (1L..8L).map { clothing(it, gray) }
        assertEquals(OutfitSuggestion.Complete, suggest(full, listOf(clothing(9L, gray, category = TOP))))
    }

    // --- Candidates and ranking ---

    @Test
    fun candidatesAreWardrobeItemsOfTheSlot_notYetInTheOutfit() {
        val clothes = listOf(
            anchor,
            clothing(2L, hue(205.0), category = BOTTOM),
            clothing(3L, hue(205.0), category = SHOES),
            clothing(4L, hue(205.0)),
            clothing(5L, hue(205.0), category = BOTTOM, inWardrobe = false),
        )

        val suggestion = suggest(listOf(anchor), clothes)

        assertEquals(OutfitSuggestion.Candidates(BOTTOM, listOf(clothes[1]), includesNotOwned = false), suggestion)
    }

    @Test
    fun includingNotOwned_addsThemAsCandidates() {
        val clothes = listOf(anchor, clothing(5L, hue(205.0), category = BOTTOM, inWardrobe = false))

        assertEquals(emptyList<Long>(), suggest(listOf(anchor), clothes).ids())
        val suggestion = suggest(listOf(anchor), clothes, includeNotOwned = true) as OutfitSuggestion.Candidates
        assertEquals(listOf(5L), suggestion.items.map { it.id })
        assertEquals(true, suggestion.includesNotOwned)
    }

    @Test
    fun clashesAreDropped_andWorksComesBeforeBorderline() {
        val clothes = listOf(
            clothing(2L, hue(270.0), category = BOTTOM), // 70 degrees off: borderline analogous
            clothing(3L, hue(300.0), category = BOTTOM), // 100 degrees off: clashes
            clothing(4L, hue(205.0), category = BOTTOM), // tonal: works
        )

        assertEquals(listOf(4L, 2L), suggest(listOf(anchor), clothes).ids())
    }

    @Test
    fun amongEquallyHarmoniousItems_theClosestToABestColorComesFirst() {
        val profile = profile(1L, "Summer", best = hue(212.0), avoid = hue(20.0))
        val clothes = listOf(
            clothing(2L, hue(195.0), category = BOTTOM),
            clothing(3L, hue(212.0), category = BOTTOM),
            clothing(4L, hue(205.0), category = BOTTOM),
        )

        assertEquals(listOf(3L, 4L, 2L), suggest(listOf(anchor), clothes, profile).ids())
    }

    @Test
    fun withoutAProfile_tiesKeepTheListOrder() {
        val clothes = listOf(
            clothing(2L, hue(195.0), category = BOTTOM),
            clothing(3L, hue(212.0), category = BOTTOM),
        )

        assertEquals(listOf(2L, 3L), suggest(listOf(anchor), clothes).ids())
    }

    @Test
    fun avoidItemsAreDropped_evenWhenTheyWork() {
        val profile = profile(1L, "Summer", best = hue(212.0), avoid = hue(195.0))
        val clothes = listOf(
            clothing(2L, hue(195.0), category = BOTTOM),
            clothing(3L, hue(205.0), category = BOTTOM),
        )

        assertEquals(listOf(3L), suggest(listOf(anchor), clothes, profile).ids())
    }

    @Test
    fun atMostThreeAreSuggested() {
        val clothes = (2L..6L).map { clothing(it, hue(200.0 + it), category = BOTTOM) }

        assertEquals(OUTFIT_SUGGESTION_COUNT, suggest(listOf(anchor), clothes).ids().size)
    }

    @Test
    fun theWholeOutfitCountsForHarmony() {
        // A 50-degree bottom works with the top alone, but not with a coat 55 degrees the other way.
        val coat = clothing(9L, hue(145.0), category = OUTERWEAR)
        val clothes = listOf(clothing(2L, hue(250.0), category = BOTTOM), clothing(3L, gray, category = BOTTOM))

        assertEquals(listOf(2L, 3L), suggest(listOf(anchor), clothes).ids())
        assertEquals(listOf(3L), suggest(listOf(anchor, coat), clothes).ids())
    }
}
