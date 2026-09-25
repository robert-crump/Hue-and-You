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

    /** Maximum number of alternative color chips shown alongside the current pick. */
    const val ALTERNATIVE_MAX_COUNT = 4

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
}
