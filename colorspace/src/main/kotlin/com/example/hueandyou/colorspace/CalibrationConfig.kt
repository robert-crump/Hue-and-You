package com.example.hueandyou.colorspace

/**
 * Starting-guess thresholds for color extraction, gathered in one place so they're easy to find
 * and retune once real photos show better values.
 */
object CalibrationConfig {
    /** The main color is measured from the box covering the middle this fraction of width and height. */
    const val CENTER_BOX_FRACTION = 0.4

    /** Number of clusters requested from the quantizer. */
    const val QUANTIZER_CLUSTER_COUNT = 16

    /** A tap-to-pick sample circle's radius, as a fraction of the image's short edge. */
    const val TAP_SAMPLE_RADIUS_FRACTION = 0.03

    /** Number of color chips shown under the photo: the most likely colors, in a fixed order. */
    const val CHIP_COUNT = 3

    /** Chips beyond the top-ranked color. */
    const val ALTERNATIVE_MAX_COUNT = CHIP_COUNT - 1

    /** Candidate colors closer than this ΔE00 to the current pick or to each other are folded together. */
    const val ALTERNATIVE_MIN_DELTA_E = 5.0

    /**
     * Candidate swatch colors below this share of the marked region are dropped. Low enough to
     * keep swatches the marked rectangle only partly covers.
     */
    const val PALETTE_IMPORT_MIN_COLOR_SHARE = 0.002

    /**
     * Palette-import pixel colors closer than this in CIELAB are treated as one swatch, folding
     * anti-aliased edges and compression noise into their swatch.
     */
    const val PALETTE_IMPORT_MERGE_DISTANCE = 6.0

    /** Maximum number of swatch colors returned from a marked region. */
    const val PALETTE_IMPORT_MAX_COLORS = 64

    /** The iris tap's sample radius: much smaller than [TAP_SAMPLE_RADIUS_FRACTION] so it stays inside the iris. */
    const val EYE_TAP_SAMPLE_RADIUS_FRACTION = 0.008

    // Season classifier. Every value below is a starting assumption, not a sourced one. Each
    // feature measure is mapped to [-1, 1] as (value - center) / halfRange, clamped.

    /** Warmth: skin hue angle (CIELAB h, degrees). Pinker skin is cooler, yellower skin warmer. */
    const val SEASON_SKIN_HUE_CENTER = 55.0
    const val SEASON_SKIN_HUE_HALF_RANGE = 15.0

    /**
     * Warmth: hair chroma along [SEASON_HAIR_WARM_HUE] (golden to red), so ash and blue-black
     * hair read cool and golden, copper or red hair warm.
     */
    const val SEASON_HAIR_WARM_HUE = 60.0
    const val SEASON_HAIR_WARM_CHROMA_CENTER = 10.0
    const val SEASON_HAIR_WARM_CHROMA_HALF_RANGE = 10.0

    const val SEASON_WARMTH_SKIN_WEIGHT = 0.7
    const val SEASON_WARMTH_HAIR_WEIGHT = 0.3

    /** Depth: L* per feature, darker is deeper. Skin's range follows ToneSense (L* 70 light, 45 deep). */
    const val SEASON_SKIN_L_CENTER = 57.5
    const val SEASON_SKIN_L_HALF_RANGE = 12.5
    const val SEASON_HAIR_L_CENTER = 40.0
    const val SEASON_HAIR_L_HALF_RANGE = 25.0
    const val SEASON_EYES_L_CENTER = 40.0
    const val SEASON_EYES_L_HALF_RANGE = 20.0

    const val SEASON_DEPTH_HAIR_WEIGHT = 0.5
    const val SEASON_DEPTH_SKIN_WEIGHT = 0.3
    const val SEASON_DEPTH_EYES_WEIGHT = 0.2

    /** Clarity: the L* gap between hair and skin, and the iris's C*ab. */
    const val SEASON_CONTRAST_L_CENTER = 30.0
    const val SEASON_CONTRAST_L_HALF_RANGE = 20.0
    const val SEASON_EYES_CHROMA_CENTER = 12.0
    const val SEASON_EYES_CHROMA_HALF_RANGE = 8.0

    const val SEASON_CLARITY_CONTRAST_WEIGHT = 0.6
    const val SEASON_CLARITY_EYES_WEIGHT = 0.4

    /** Season fit = score toward the dominant pole + this factor x score toward the secondary pole. */
    const val SEASON_SECONDARY_FACTOR = 0.5

    /** Fits map linearly onto a match of 0-100 % between these two values (clamped). */
    const val SEASON_MATCH_MIN_FIT = -1.5
    const val SEASON_MATCH_MAX_FIT = 1.5
}
