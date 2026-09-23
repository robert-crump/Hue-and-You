package com.example.hueandyou.colorspace

/**
 * Starting-guess thresholds for white-balance calibration and color extraction, gathered in one
 * place so they're easy to find and retune once real photos show better values.
 */
object CalibrationConfig {
    /** Radius of the circular white-sheet sample, as a fraction of the image's short edge. */
    const val WHITE_SAMPLE_RADIUS_FRACTION = 0.03

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
}
