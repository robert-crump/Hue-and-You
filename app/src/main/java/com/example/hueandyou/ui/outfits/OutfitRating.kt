package com.example.hueandyou.ui.outfits

import com.example.hueandyou.colorspace.ClothingVerdict
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.OutfitHarmony
import com.example.hueandyou.colorspace.OutfitHarmonyConfig
import com.example.hueandyou.colorspace.OutfitHarmonyResult
import com.example.hueandyou.colorspace.PaletteScorer
import com.example.hueandyou.data.profile.Profile

/** Fewest and most items an outfit can be saved and rated with. */
const val OUTFIT_MIN_ITEMS = OutfitHarmonyConfig.MIN_COLORS
const val OUTFIT_MAX_ITEMS = OutfitHarmonyConfig.MAX_COLORS

/**
 * "Suits you": every item's color re-scored against one profile. [avoidIndices] are the positions
 * of the items whose verdict is Avoid, in outfit order.
 */
data class SuitsYouSummary(
    val itemCount: Int,
    val bestCount: Int,
    val avoidIndices: List<Int>,
) {
    val avoidCount: Int get() = avoidIndices.size

    /** One verdict for the list row: any Avoid item wins, then a majority of Best items, else neither. */
    val overall: ClothingVerdict
        get() = when {
            avoidCount > 0 -> ClothingVerdict.AVOID
            bestCount * 2 > itemCount -> ClothingVerdict.YES
            else -> ClothingVerdict.NEITHER
        }
}

/** An outfit's two live verdicts. [suitsYou] is null without a profile to score against. */
data class OutfitRating(
    val suitsYou: SuitsYouSummary?,
    val harmony: OutfitHarmonyResult,
)

/** Rates the item colors (in outfit order), or null if there are too few or too many to rate. */
fun rateOutfit(colorsArgb: List<Int>, profile: Profile?, wheel: HarmonyWheel): OutfitRating? {
    if (colorsArgb.size !in OUTFIT_MIN_ITEMS..OUTFIT_MAX_ITEMS) return null
    return OutfitRating(
        suitsYou = profile?.let { suitsYou(colorsArgb, it) },
        harmony = OutfitHarmony.classify(colorsArgb, wheel),
    )
}

private fun suitsYou(colorsArgb: List<Int>, profile: Profile): SuitsYouSummary {
    val best = profile.bestColors.map { it.argb }
    val avoid = profile.avoidColors.map { it.argb }
    val verdicts = colorsArgb.map { ClothingVerdict.forScore(PaletteScorer.score(it, best, avoid)) }
    return SuitsYouSummary(
        itemCount = colorsArgb.size,
        bestCount = verdicts.count { it == ClothingVerdict.YES },
        avoidIndices = verdicts.indices.filter { verdicts[it] == ClothingVerdict.AVOID },
    )
}
