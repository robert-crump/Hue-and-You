package com.example.hueandyou.colorspace

/**
 * The single verdict shown for a Rate Clothing result, derived from a [PaletteScore] rather than
 * stored - so History entries scored before this existed still get one.
 */
enum class ClothingVerdict {
    YES, AVOID, NEITHER;

    companion object {
        /** ΔE00 beyond which a nearest Best/Avoid color no longer counts as close enough to call. */
        const val VERDICT_MAX_DELTA_E = 12.0

        fun forScore(score: PaletteScore): ClothingVerdict {
            val bestDeltaE = score.nearestBest?.deltaE
            val avoidDeltaE = score.nearestAvoid?.deltaE
            val isYes = bestDeltaE != null && bestDeltaE <= VERDICT_MAX_DELTA_E &&
                (avoidDeltaE == null || bestDeltaE < avoidDeltaE)
            val isAvoid = avoidDeltaE != null && avoidDeltaE <= VERDICT_MAX_DELTA_E &&
                (bestDeltaE == null || avoidDeltaE < bestDeltaE)
            return when {
                isYes -> YES
                isAvoid -> AVOID
                else -> NEITHER
            }
        }
    }
}
