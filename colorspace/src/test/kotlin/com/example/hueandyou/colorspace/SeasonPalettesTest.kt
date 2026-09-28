package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonPalettesTest {

    @Test
    fun everySeasonHasTwentyDistinctBestColors() {
        for (season in Season.entries) {
            val best = SeasonPalettes.best(season)
            assertEquals("$season", 20, best.size)
            assertEquals("$season has duplicates", 20, best.toSet().size)
        }
    }

    @Test
    fun bestByCategoryFollowsStoredOrder() {
        for (season in Season.entries) {
            assertEquals(
                SeasonPalettes.best(season),
                PaletteCategory.entries.flatMap { SeasonPalettes.best(season, it) },
            )
        }
    }

    @Test
    fun oppositeIsAnInvolutionWithoutFixedPoints() {
        for (season in Season.entries) {
            assertNotEquals(season, season.opposite)
            assertEquals(season, season.opposite.opposite)
        }
    }

    @Test
    fun oppositePairsMatchTheSpec() {
        val pairs = mapOf(
            Season.LIGHT_SPRING to Season.DEEP_WINTER,
            Season.LIGHT_SUMMER to Season.DEEP_AUTUMN,
            Season.TRUE_SPRING to Season.TRUE_SUMMER,
            Season.TRUE_AUTUMN to Season.TRUE_WINTER,
            Season.BRIGHT_SPRING to Season.SOFT_SUMMER,
            Season.BRIGHT_WINTER to Season.SOFT_AUTUMN,
        )
        for ((a, b) in pairs) {
            assertEquals(b, a.opposite)
            assertEquals(a, b.opposite)
        }
    }

    @Test
    fun oppositeFlipsEveryTrait() {
        for (season in Season.entries) {
            val traits = traitsOf(season)
            val opposite = traitsOf(season.opposite)
            assertEquals("$season warmth", -traits.warmth, opposite.warmth)
            assertEquals("$season depth", -traits.depth, opposite.depth)
            assertEquals("$season clarity", -traits.clarity, opposite.clarity)
        }
    }

    @Test
    fun avoidIsTwoPerCategoryFromTheOppositeBestInCategoryOrder() {
        for (season in Season.entries) {
            val expected = PaletteCategory.entries.flatMap {
                SeasonPalettes.best(season.opposite, it).take(2)
            }
            assertEquals("$season", expected, SeasonPalettes.avoid(season))
            assertEquals(10, SeasonPalettes.avoid(season).size)
        }
    }

    @Test
    fun avoidNeverOverlapsBest() {
        for (season in Season.entries) {
            val overlap = SeasonPalettes.avoid(season).intersect(SeasonPalettes.best(season).toSet())
            assertTrue("$season: ${overlap.map(::formatHexColor)}", overlap.isEmpty())
        }
    }

    /** Every Best color fits its season's axes in HCT. */
    @Test
    fun bestColorsFitTheirSeason() {
        val violations = Season.entries.flatMap { season ->
            PaletteCategory.entries.flatMap { category ->
                SeasonPalettes.best(season, category).mapNotNull { argb ->
                    val problem = axisViolation(season, category, argbToHct(argb))
                    problem?.let { "$season $category ${formatHexColor(argb)} ${describe(argb)}: $it" }
                }
            }
        }
        assertTrue(violations.joinToString("\n", prefix = "\n"), violations.isEmpty())
    }

    /** Lights are light and Darks are dark in every season, so the categories read as such. */
    @Test
    fun categoriesHaveTheirTone() {
        val violations = Season.entries.flatMap { season ->
            SeasonPalettes.best(season, PaletteCategory.LIGHTS)
                .filter { argbToHct(it).tone < LIGHTS_MIN_TONE }
                .map { "$season LIGHTS ${formatHexColor(it)} ${describe(it)}" } +
                SeasonPalettes.best(season, PaletteCategory.DARKS)
                    .filter { argbToHct(it).tone > DARKS_MAX_TONE }
                    .map { "$season DARKS ${formatHexColor(it)} ${describe(it)}" }
        }
        assertTrue(violations.joinToString("\n", prefix = "\n"), violations.isEmpty())
    }

    private fun axisViolation(season: Season, category: PaletteCategory, hct: Hct): String? {
        val warm = traitsOf(season).warmth > 0
        val chromatic = hct.chroma >= HUE_CHECK_MIN_CHROMA
        return when {
            chromatic && warm && !isWarmHue(hct.hue) -> "not a warm hue"
            chromatic && !warm && !isCoolHue(hct.hue) -> "not a cool hue"
            season in LIGHT_SEASONS && hct.tone < LIGHT_MIN_TONE -> "too dark for a Light season"
            season in DEEP_SEASONS && category in DEEP_CATEGORIES && hct.tone > DEEP_MAX_TONE ->
                "too light for a Deep season"
            season in DEEP_SEASONS && category == PaletteCategory.DARKS && hct.tone > DEEP_DARKS_MAX_TONE ->
                "Darks too light for a Deep season"
            season in BRIGHT_SEASONS && category in BRIGHT_CATEGORIES && hct.chroma < BRIGHT_MIN_CHROMA ->
                "too muted for a Bright season"
            season in SOFT_SEASONS && hct.chroma > SOFT_MAX_CHROMA -> "too saturated for a Soft season"
            else -> null
        }
    }

    private fun isWarmHue(hue: Double) = hue in WARM_HUE_MIN..WARM_HUE_MAX

    private fun isCoolHue(hue: Double) = hue >= COOL_HUE_FROM || hue <= COOL_HUE_TO

    private fun describe(argb: Int): String = argbToHct(argb).let {
        "(h=%.0f c=%.0f t=%.0f)".format(it.hue, it.chroma, it.tone)
    }

    /** +1/-1/0 per axis: warm/cool, light/deep, bright/soft. True seasons lean soft (Summer, Autumn) or bright (Spring, Winter). */
    private data class Traits(val warmth: Int, val depth: Int, val clarity: Int)

    private fun traitsOf(season: Season): Traits = when (season) {
        Season.LIGHT_SPRING -> Traits(warmth = 1, depth = 1, clarity = 0)
        Season.TRUE_SPRING -> Traits(warmth = 1, depth = 0, clarity = 1)
        Season.BRIGHT_SPRING -> Traits(warmth = 1, depth = 0, clarity = 1)
        Season.LIGHT_SUMMER -> Traits(warmth = -1, depth = 1, clarity = 0)
        Season.TRUE_SUMMER -> Traits(warmth = -1, depth = 0, clarity = -1)
        Season.SOFT_SUMMER -> Traits(warmth = -1, depth = 0, clarity = -1)
        Season.SOFT_AUTUMN -> Traits(warmth = 1, depth = 0, clarity = -1)
        Season.TRUE_AUTUMN -> Traits(warmth = 1, depth = 0, clarity = -1)
        Season.DEEP_AUTUMN -> Traits(warmth = 1, depth = -1, clarity = 0)
        Season.DEEP_WINTER -> Traits(warmth = -1, depth = -1, clarity = 0)
        Season.TRUE_WINTER -> Traits(warmth = -1, depth = 0, clarity = 1)
        Season.BRIGHT_WINTER -> Traits(warmth = -1, depth = 0, clarity = 1)
    }

    private companion object {
        /** Below this chroma a color reads as neutral and its hue says nothing about warmth. */
        const val HUE_CHECK_MIN_CHROMA = 12.0

        /** Warm hues run from red through orange, yellow and green to teal. */
        const val WARM_HUE_MIN = 15.0
        const val WARM_HUE_MAX = 200.0

        /** Cool hues run from green through blue, violet and pink to blue-red (wrapping 360). */
        const val COOL_HUE_FROM = 140.0
        const val COOL_HUE_TO = 30.0

        val LIGHT_SEASONS = setOf(Season.LIGHT_SPRING, Season.LIGHT_SUMMER)
        val DEEP_SEASONS = setOf(Season.DEEP_AUTUMN, Season.DEEP_WINTER)
        val BRIGHT_SEASONS = setOf(Season.BRIGHT_SPRING, Season.BRIGHT_WINTER)
        val SOFT_SEASONS = setOf(Season.SOFT_AUTUMN, Season.SOFT_SUMMER)

        const val LIGHT_MIN_TONE = 40.0
        const val DEEP_MAX_TONE = 60.0
        const val DEEP_DARKS_MAX_TONE = 22.0
        val DEEP_CATEGORIES = setOf(PaletteCategory.MID_TONES, PaletteCategory.ACCENTS)

        /** sRGB caps turquoise near chroma 50, so this sits a little under that ceiling. */
        const val BRIGHT_MIN_CHROMA = 45.0
        val BRIGHT_CATEGORIES = setOf(PaletteCategory.MID_TONES, PaletteCategory.ACCENTS)
        const val SOFT_MAX_CHROMA = 40.0

        const val LIGHTS_MIN_TONE = 70.0
        const val DARKS_MAX_TONE = 50.0
    }
}
