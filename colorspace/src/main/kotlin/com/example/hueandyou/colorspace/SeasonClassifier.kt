package com.example.hueandyou.colorspace

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The three season axes; each score runs from -1 at its [negative] pole to +1 at its [positive] pole. */
enum class SeasonAxis { WARMTH, DEPTH, CLARITY }

/** One pole of a [SeasonAxis]. */
enum class SeasonTrait(val axis: SeasonAxis, val sign: Int) {
    WARM(SeasonAxis.WARMTH, 1),
    COOL(SeasonAxis.WARMTH, -1),
    DEEP(SeasonAxis.DEPTH, 1),
    LIGHT(SeasonAxis.DEPTH, -1),
    BRIGHT(SeasonAxis.CLARITY, 1),
    SOFT(SeasonAxis.CLARITY, -1),
}

/** The season's main trait: Light Spring is first of all light, True Autumn warm, Soft Summer soft. */
val Season.dominantTrait: SeasonTrait
    get() = when (this) {
        Season.LIGHT_SPRING, Season.LIGHT_SUMMER -> SeasonTrait.LIGHT
        Season.DEEP_AUTUMN, Season.DEEP_WINTER -> SeasonTrait.DEEP
        Season.TRUE_SPRING, Season.TRUE_AUTUMN -> SeasonTrait.WARM
        Season.TRUE_SUMMER, Season.TRUE_WINTER -> SeasonTrait.COOL
        Season.BRIGHT_SPRING, Season.BRIGHT_WINTER -> SeasonTrait.BRIGHT
        Season.SOFT_AUTUMN, Season.SOFT_SUMMER -> SeasonTrait.SOFT
    }

/** The trait that splits the two seasons sharing a [dominantTrait]. */
val Season.secondaryTrait: SeasonTrait
    get() = when (this) {
        Season.LIGHT_SPRING, Season.DEEP_AUTUMN, Season.BRIGHT_SPRING, Season.SOFT_AUTUMN -> SeasonTrait.WARM
        Season.LIGHT_SUMMER, Season.DEEP_WINTER, Season.BRIGHT_WINTER, Season.SOFT_SUMMER -> SeasonTrait.COOL
        Season.TRUE_SPRING, Season.TRUE_WINTER -> SeasonTrait.BRIGHT
        Season.TRUE_AUTUMN, Season.TRUE_SUMMER -> SeasonTrait.SOFT
    }

/** A person's position on the three axes, each in [-1, 1]: +1 is warm, deep and bright. */
data class SeasonAxes(val warmth: Double, val depth: Double, val clarity: Double) {
    fun score(axis: SeasonAxis): Double = when (axis) {
        SeasonAxis.WARMTH -> warmth
        SeasonAxis.DEPTH -> depth
        SeasonAxis.CLARITY -> clarity
    }

    /** How far toward [trait]'s pole: the axis score, flipped for the negative pole. */
    fun toward(trait: SeasonTrait): Double = score(trait.axis) * trait.sign
}

/** A season with its [fit] (roughly -1.5..1.5) and the [matchPercent] shown for it. */
data class SeasonMatch(val season: Season, val fit: Double) {
    val matchPercent: Int get() = SeasonClassifier.matchPercent(fit)
}

/**
 * Classifies skin, hair and eye colors into one of the 12 seasons. All weights and ranges live in
 * [CalibrationConfig] and are starting assumptions awaiting calibration against real photos.
 */
object SeasonClassifier {

    /** All 12 seasons, best fit first. */
    fun classify(skin: Int, hair: Int, eyes: Int): List<SeasonMatch> = rank(axes(skin, hair, eyes))

    fun axes(skin: Int, hair: Int, eyes: Int): SeasonAxes {
        val skinLab = argbToLab(skin)
        val hairLab = argbToLab(hair)
        val eyesLab = argbToLab(eyes)
        with(CalibrationConfig) {
            val skinWarmth = normalize(hueAngle(skinLab), SEASON_SKIN_HUE_CENTER, SEASON_SKIN_HUE_HALF_RANGE)
            val hairWarmth = normalize(
                chromaAlong(hairLab, SEASON_HAIR_WARM_HUE),
                SEASON_HAIR_WARM_CHROMA_CENTER,
                SEASON_HAIR_WARM_CHROMA_HALF_RANGE,
            )
            val warmth = SEASON_WARMTH_SKIN_WEIGHT * skinWarmth + SEASON_WARMTH_HAIR_WEIGHT * hairWarmth

            // Lower L* is deeper, so the centers subtract the other way round.
            val depth = SEASON_DEPTH_HAIR_WEIGHT * normalize(SEASON_HAIR_L_CENTER, hairLab.l, SEASON_HAIR_L_HALF_RANGE) +
                SEASON_DEPTH_SKIN_WEIGHT * normalize(SEASON_SKIN_L_CENTER, skinLab.l, SEASON_SKIN_L_HALF_RANGE) +
                SEASON_DEPTH_EYES_WEIGHT * normalize(SEASON_EYES_L_CENTER, eyesLab.l, SEASON_EYES_L_HALF_RANGE)

            val contrast = abs(skinLab.l - hairLab.l)
            val clarity = SEASON_CLARITY_CONTRAST_WEIGHT *
                normalize(contrast, SEASON_CONTRAST_L_CENTER, SEASON_CONTRAST_L_HALF_RANGE) +
                SEASON_CLARITY_EYES_WEIGHT *
                normalize(chroma(eyesLab), SEASON_EYES_CHROMA_CENTER, SEASON_EYES_CHROMA_HALF_RANGE)

            return SeasonAxes(warmth = warmth, depth = depth, clarity = clarity)
        }
    }

    /** All 12 seasons ranked by fit, best first; ties keep [Season] order. */
    fun rank(axes: SeasonAxes): List<SeasonMatch> = Season.entries
        .map { SeasonMatch(it, fit(it, axes)) }
        .sortedByDescending { it.fit }

    fun fit(season: Season, axes: SeasonAxes): Double =
        axes.toward(season.dominantTrait) +
            CalibrationConfig.SEASON_SECONDARY_FACTOR * axes.toward(season.secondaryTrait)

    /** [fit] mapped linearly onto 0-100, clamped. */
    fun matchPercent(fit: Double): Int {
        val min = CalibrationConfig.SEASON_MATCH_MIN_FIT
        val max = CalibrationConfig.SEASON_MATCH_MAX_FIT
        return ((fit - min) / (max - min) * 100).coerceIn(0.0, 100.0).roundToInt()
    }

    private fun normalize(value: Double, center: Double, halfRange: Double): Double =
        ((value - center) / halfRange).coerceIn(-1.0, 1.0)

    private fun chroma(lab: Lab): Double = hypot(lab.a, lab.b)

    /** CIELAB hue angle in degrees, 0-360. */
    private fun hueAngle(lab: Lab): Double {
        val degrees = Math.toDegrees(atan2(lab.b, lab.a))
        return if (degrees < 0) degrees + 360 else degrees
    }

    /** The part of [lab]'s chroma pointing toward [hueDegrees] (negative when it points away). */
    private fun chromaAlong(lab: Lab, hueDegrees: Double): Double =
        chroma(lab) * cos(Math.toRadians(hueAngle(lab) - hueDegrees))
}
