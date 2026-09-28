package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun lab(l: Double, a: Double, b: Double): Int = labToArgb(Lab(l, a, b))

/** A made-up person's skin, hair and eye colors, as CIELAB values picked to sit clearly in one season. */
private data class Triplet(val skin: Int, val hair: Int, val eyes: Int)

/**
 * One reference triplet per season. They document where the current [CalibrationConfig] puts
 * typical coloring, so a calibration change shows which seasons move.
 */
private val referenceTriplets: Map<Season, Triplet> = mapOf(
    // Warm fair skin, golden blonde hair, light blue eyes.
    Season.LIGHT_SPRING to Triplet(lab(75.0, 10.0, 22.0), lab(65.0, 5.0, 25.0), lab(55.0, -5.0, -15.0)),
    // Pink fair skin, ash blonde hair, grey-blue eyes.
    Season.LIGHT_SUMMER to Triplet(lab(75.0, 14.0, 9.0), lab(62.0, 1.0, 6.0), lab(55.0, -3.0, -8.0)),
    // Golden skin, golden brown hair, clear green eyes.
    Season.TRUE_SPRING to Triplet(lab(68.0, 10.0, 26.0), lab(42.0, 14.0, 30.0), lab(45.0, -15.0, 20.0)),
    // Golden skin, auburn hair, muted hazel eyes.
    Season.TRUE_AUTUMN to Triplet(lab(62.0, 11.0, 26.0), lab(35.0, 18.0, 25.0), lab(38.0, 2.0, 6.7)),
    // Light warm skin, dark golden brown hair, bright turquoise eyes.
    Season.BRIGHT_SPRING to Triplet(lab(70.0, 11.0, 20.0), lab(25.0, 8.0, 16.0), lab(50.0, -20.0, -5.0)),
    // Light neutral-cool skin, black hair, icy blue eyes.
    Season.BRIGHT_WINTER to Triplet(lab(70.0, 12.0, 13.0), lab(15.0, 0.5, 1.0), lab(55.0, -8.0, -22.0)),
    // Pink skin, ash brown hair, grey-blue eyes.
    Season.TRUE_SUMMER to Triplet(lab(65.0, 14.0, 9.0), lab(40.0, 1.0, 5.0), lab(50.0, -3.0, -8.0)),
    // Pink skin, near-black hair, bright blue eyes.
    Season.TRUE_WINTER to Triplet(lab(60.0, 13.0, 8.0), lab(22.0, 1.0, 2.0), lab(45.0, -6.0, -18.0)),
    // Deep golden skin, dark warm brown hair, dark brown eyes.
    Season.DEEP_AUTUMN to Triplet(lab(48.0, 13.0, 24.0), lab(22.0, 8.0, 14.0), lab(25.0, 6.0, 10.0)),
    // Deep cool skin, black hair, near-black eyes.
    Season.DEEP_WINTER to Triplet(lab(48.0, 12.0, 9.0), lab(15.0, 0.5, 1.0), lab(22.0, 3.0, 4.0)),
    // Warm skin, dark blonde hair, muted hazel eyes.
    Season.SOFT_AUTUMN to Triplet(lab(65.0, 11.0, 20.0), lab(50.0, 5.0, 13.0), lab(45.0, 2.0, 7.0)),
    // Neutral-cool skin, ash brown hair, grey eyes.
    Season.SOFT_SUMMER to Triplet(lab(65.0, 12.0, 13.0), lab(48.0, 1.0, 5.0), lab(48.0, -1.0, -3.0)),
)

class SeasonClassifierTest {

    @Test
    fun everySeasonHasAReferenceTriplet() {
        assertEquals(Season.entries.toSet(), referenceTriplets.keys)
    }

    @Test
    fun referenceTripletsClassifyAsTheirSeason() {
        for ((season, triplet) in referenceTriplets) {
            val ranked = SeasonClassifier.classify(triplet.skin, triplet.hair, triplet.eyes)
            assertEquals("${SeasonClassifier.axes(triplet.skin, triplet.hair, triplet.eyes)} -> $ranked", season, ranked.first().season)
        }
    }

    @Test
    fun rankListsAllTwelveSeasonsBestFirst() {
        val ranked = SeasonClassifier.rank(SeasonAxes(warmth = 0.3, depth = -0.2, clarity = 0.6))

        assertEquals(Season.entries.toSet(), ranked.map { it.season }.toSet())
        assertEquals(ranked.sortedByDescending { it.fit }, ranked)
    }

    @Test
    fun fitIsDominantScorePlusHalfTheSecondaryScore() {
        val axes = SeasonAxes(warmth = 0.4, depth = -0.8, clarity = 0.2)

        assertEquals(0.8 + 0.5 * 0.4, SeasonClassifier.fit(Season.LIGHT_SPRING, axes), 1e-9)
        assertEquals(0.8 - 0.5 * 0.4, SeasonClassifier.fit(Season.LIGHT_SUMMER, axes), 1e-9)
        assertEquals(0.4 - 0.5 * 0.2, SeasonClassifier.fit(Season.TRUE_AUTUMN, axes), 1e-9)
        assertEquals(-0.2 - 0.5 * 0.4, SeasonClassifier.fit(Season.SOFT_SUMMER, axes), 1e-9)
    }

    @Test
    fun clearDominantAndSecondaryTraitsPickThatSeason() {
        for (season in Season.entries) {
            val scores = mutableMapOf(SeasonAxis.WARMTH to 0.0, SeasonAxis.DEPTH to 0.0, SeasonAxis.CLARITY to 0.0)
            scores[season.dominantTrait.axis] = 0.9 * season.dominantTrait.sign
            scores[season.secondaryTrait.axis] = 0.5 * season.secondaryTrait.sign
            val axes = SeasonAxes(
                warmth = scores.getValue(SeasonAxis.WARMTH),
                depth = scores.getValue(SeasonAxis.DEPTH),
                clarity = scores.getValue(SeasonAxis.CLARITY),
            )

            assertEquals(season, SeasonClassifier.rank(axes).first().season)
        }
    }

    @Test
    fun dominantAndSecondaryTraitsSitOnDifferentAxes() {
        for (season in Season.entries) {
            assertTrue("$season", season.dominantTrait.axis != season.secondaryTrait.axis)
        }
    }

    @Test
    fun oppositeSeasonFlipsBothTraits() {
        for (season in Season.entries) {
            val opposite = season.opposite
            assertEquals("$season", season.dominantTrait.axis, opposite.dominantTrait.axis)
            assertEquals("$season", -season.dominantTrait.sign, opposite.dominantTrait.sign)
            assertEquals("$season", -season.secondaryTrait.sign, opposite.secondaryTrait.sign)
        }
    }

    @Test
    fun matchPercentMapsFitLinearlyAndClamps() {
        assertEquals(0, SeasonClassifier.matchPercent(-1.5))
        assertEquals(50, SeasonClassifier.matchPercent(0.0))
        assertEquals(75, SeasonClassifier.matchPercent(0.75))
        assertEquals(100, SeasonClassifier.matchPercent(1.5))
        assertEquals(0, SeasonClassifier.matchPercent(-3.0))
        assertEquals(100, SeasonClassifier.matchPercent(2.0))
        assertEquals(50, SeasonMatch(Season.TRUE_SPRING, 0.0).matchPercent)
    }

    @Test
    fun axesStayWithinUnitRange() {
        val extremes = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0xFFFFFF00.toInt())
        for (skin in extremes) for (hair in extremes) for (eyes in extremes) {
            val axes = SeasonClassifier.axes(skin, hair, eyes)
            for (axis in SeasonAxis.entries) {
                assertTrue("$axes", axes.score(axis) in -1.0..1.0)
            }
        }
    }
}
