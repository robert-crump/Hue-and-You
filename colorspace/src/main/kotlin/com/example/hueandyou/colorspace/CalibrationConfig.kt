package com.example.hueandyou.colorspace

/**
 * Starting-guess thresholds for white-balance calibration and color extraction, gathered in one
 * place so they're easy to find and retune once real photos show better values.
 */
object CalibrationConfig {
    /** Radius of the circular white-sheet sample, as a fraction of the image's short edge. */
    const val WHITE_SAMPLE_RADIUS_FRACTION = 0.03

    /** A channel value (0-255) at or above this is considered blown out/clipped. */
    const val CLIPPED_CHANNEL_THRESHOLD = 250

    /** The sample fails as [WhiteBalanceFailureReason.CLIPPED] once more than this share of its pixels are clipped. */
    const val CLIPPED_PIXEL_FRACTION_THRESHOLD = 0.05

    /** The sample fails as [WhiteBalanceFailureReason.TOO_DARK] below this average Lab lightness (0-100). */
    const val MIN_WHITE_LUMINANCE_L = 50.0

    /** The sample fails as [WhiteBalanceFailureReason.NOT_WHITE] above this average Lab chroma. */
    const val MAX_WHITE_CHROMA = 15.0

    /** Number of clusters requested from the quantizer, before the share filter and cap below. */
    const val QUANTIZER_CLUSTER_COUNT = 16

    /** Candidate colors below this share of the sampled area are dropped. */
    const val MIN_COLOR_SHARE = 0.05

    /** Maximum number of candidate colors returned. */
    const val MAX_COLORS = 6

    /** A top color needs at least this share of the sampled area to be "clearly dominant". */
    const val DOMINANT_MIN_SHARE = 0.60

    /** ...and needs to lead the runner-up color by at least this multiple. */
    const val DOMINANT_MIN_RATIO_TO_RUNNER_UP = 3.0

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
