package com.example.hueandyou.ui.outfits

import com.example.hueandyou.colorspace.ClothingVerdict
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.OutfitHarmony
import com.example.hueandyou.colorspace.OutfitHarmonyLevel
import com.example.hueandyou.colorspace.PaletteScorer
import com.example.hueandyou.data.history.ClothingCategory
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.profile.Profile

/** How many candidates "Complete this outfit" shows. */
const val OUTFIT_SUGGESTION_COUNT = 3

/** The optional slots, in the order Suggest fills them once the required ones are. */
private val OPTIONAL_SLOTS = listOf(ClothingCategory.OUTERWEAR, ClothingCategory.SHOES, ClothingCategory.BELT)

/** What Suggest has to offer for the outfit as it stands. */
sealed interface OutfitSuggestion {
    /** The outfit is empty: the user picks an anchor item first. */
    data object NeedsAnchor : OutfitSuggestion

    /** Every slot is filled, or the outfit is full. */
    data object Complete : OutfitSuggestion

    /**
     * The best items for [slot], at most [OUTFIT_SUGGESTION_COUNT]; empty when none fits.
     * [includesNotOwned] is whether Not owned items were considered too.
     */
    data class Candidates(
        val slot: ClothingCategory,
        val items: List<HistoryEntry>,
        val includesNotOwned: Boolean,
    ) : OutfitSuggestion
}

/**
 * The next slot to fill: the required Top + Bottom (or a One-piece) first, then Outerwear, Shoes and
 * Belt. With neither a Top, a Bottom nor a One-piece yet, the Top comes first. Uncategorized items
 * fill nothing. Null once every slot is filled.
 */
fun nextOutfitSlot(categories: Collection<ClothingCategory?>): ClothingCategory? {
    val hasTop = ClothingCategory.TOP in categories
    val hasBottom = ClothingCategory.BOTTOM in categories
    val required = when {
        ClothingCategory.ONE_PIECE in categories || (hasTop && hasBottom) -> null
        hasTop -> ClothingCategory.BOTTOM
        else -> ClothingCategory.TOP
    }
    return required ?: OPTIONAL_SLOTS.firstOrNull { it !in categories }
}

/**
 * "Complete this outfit": the next slot and the Clothes items that best fill it. Candidates are the
 * [clothes] of that category not already in [outfit] (Wardrobe only unless [includeNotOwned]),
 * without an Avoid verdict against [profile], whose colors don't clash with the outfit's; Works
 * before Borderline, then closest to a Best color. Ties keep [clothes]' order.
 */
fun suggestOutfitItems(
    outfit: List<HistoryEntry>,
    clothes: List<HistoryEntry>,
    profile: Profile?,
    wheel: HarmonyWheel,
    includeNotOwned: Boolean,
): OutfitSuggestion {
    if (outfit.isEmpty()) return OutfitSuggestion.NeedsAnchor
    if (outfit.size >= OUTFIT_MAX_ITEMS) return OutfitSuggestion.Complete
    val slot = nextOutfitSlot(outfit.map { it.category }) ?: return OutfitSuggestion.Complete

    val outfitIds = outfit.mapTo(HashSet()) { it.id }
    val outfitColors = outfit.map { it.calibratedArgb }
    val best = profile?.bestColors?.map { it.argb }.orEmpty()
    val avoid = profile?.avoidColors?.map { it.argb }.orEmpty()

    val ranked = clothes
        .filter { it.category == slot && it.id !in outfitIds && (includeNotOwned || it.inWardrobe) }
        .mapNotNull { item ->
            val score = PaletteScorer.score(item.calibratedArgb, best, avoid)
            if (profile != null && ClothingVerdict.forScore(score) == ClothingVerdict.AVOID) return@mapNotNull null
            val level = OutfitHarmony.classify(outfitColors + item.calibratedArgb, wheel).level
            if (level == OutfitHarmonyLevel.CLASHES) return@mapNotNull null
            RankedItem(item, level, score.nearestBest?.deltaE ?: Double.POSITIVE_INFINITY)
        }
        .sortedWith(compareBy<RankedItem> { it.level.ordinal }.thenBy { it.bestDeltaE })
        .take(OUTFIT_SUGGESTION_COUNT)
        .map { it.item }
    return OutfitSuggestion.Candidates(slot, ranked, includeNotOwned)
}

private data class RankedItem(val item: HistoryEntry, val level: OutfitHarmonyLevel, val bestDeltaE: Double)
