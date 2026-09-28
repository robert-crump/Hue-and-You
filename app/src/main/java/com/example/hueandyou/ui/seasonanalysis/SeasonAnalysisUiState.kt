package com.example.hueandyou.ui.seasonanalysis

import com.example.hueandyou.colorspace.Season
import com.example.hueandyou.colorspace.SeasonMatch
import com.example.hueandyou.colorspace.SeasonPalettes

/** The three colors the season is read from, in step order, each from its own photo. */
enum class SeasonFeature {
    SKIN, HAIR, EYES;

    /** The step after this one, or null after the last. */
    fun next(): SeasonFeature? = entries.getOrNull(ordinal + 1)

    /** The step before this one, or null before the first. */
    fun previous(): SeasonFeature? = entries.getOrNull(ordinal - 1)
}

/** A picked color and where the marker sat, in pixels of that feature's photo. */
data class FeaturePick(val argb: Int, val x: Int, val y: Int)

/** How many seasons the result screen lists. */
const val SEASON_RESULT_COUNT = 3

sealed interface SeasonAnalysisUiState {
    /** The camera for [feature]'s photo; [picks] are the features finished before it. */
    data class Capturing(
        val feature: SeasonFeature = SeasonFeature.SKIN,
        val picks: Map<SeasonFeature, FeaturePick> = emptyMap(),
    ) : SeasonAnalysisUiState

    data class LoadingPhoto(
        val feature: SeasonFeature,
        val picks: Map<SeasonFeature, FeaturePick>,
    ) : SeasonAnalysisUiState

    /** Moving the marker on [feature]'s photo; [marker] is its live pick, [picks] the finished features. */
    data class Placing(
        val feature: SeasonFeature,
        val picks: Map<SeasonFeature, FeaturePick>,
        val marker: FeaturePick,
    ) : SeasonAnalysisUiState

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
