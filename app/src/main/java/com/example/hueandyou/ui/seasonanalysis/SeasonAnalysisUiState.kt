package com.example.hueandyou.ui.seasonanalysis

import com.example.hueandyou.colorspace.Season
import com.example.hueandyou.colorspace.SeasonMatch
import com.example.hueandyou.colorspace.SeasonPalettes

/** The three colors the season is read from, in pick order. */
enum class SeasonFeature { SKIN, HAIR, EYES }

/** A picked color and where it was tapped, in bitmap pixels. */
data class FeaturePick(val argb: Int, val x: Int, val y: Int)

/** How many seasons the result screen lists. */
const val SEASON_RESULT_COUNT = 3

/**
 * The feature to pick after [after]: the next one without a pick, in [SeasonFeature] order and
 * wrapping around, or null once all three are set.
 */
fun nextMissingFeature(picks: Map<SeasonFeature, FeaturePick>, after: SeasonFeature): SeasonFeature? {
    val order = SeasonFeature.entries
    return (1..order.size)
        .map { order[(after.ordinal + it) % order.size] }
        .firstOrNull { it !in picks }
}

sealed interface SeasonAnalysisUiState {
    data object PickingPhoto : SeasonAnalysisUiState
    data object LoadingPhoto : SeasonAnalysisUiState

    /** Taps on the photo fill [active]'s pick; null once all three are set until a chip is tapped. */
    data class PickingColors(
        val picks: Map<SeasonFeature, FeaturePick> = emptyMap(),
        val active: SeasonFeature? = SeasonFeature.SKIN,
    ) : SeasonAnalysisUiState {
        val canContinue: Boolean get() = picks.size == SeasonFeature.entries.size
    }

    /** [topMatches] are the best [SEASON_RESULT_COUNT] seasons, best first; [picks] allow going back. */
    data class ShowingResult(
        val picks: Map<SeasonFeature, FeaturePick>,
        val topMatches: List<SeasonMatch>,
        val selected: Season,
    ) : SeasonAnalysisUiState {
        val bestColors: List<Int> get() = SeasonPalettes.best(selected)
        val avoidColors: List<Int> get() = SeasonPalettes.avoid(selected)
    }

    data object Saving : SeasonAnalysisUiState

    /** The season's profile was created as [profileId]. */
    data class Done(val profileId: Long) : SeasonAnalysisUiState
}
