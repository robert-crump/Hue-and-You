package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewColorsTest {

    private fun hex(value: String) = requireNotNull(parseHexColor(value))

    @Test
    fun startsWithTheMostColorfulColor() {
        val colors = listOf(hex("#808080"), hex("#C08080"), hex("#FF0000"))
        assertEquals(hex("#FF0000"), previewColors(colors, 4).first())
    }

    @Test
    fun spreadsHuesInsteadOfTakingTheTopChromaColors() {
        val reds = listOf(hex("#FF0000"), hex("#F00010"), hex("#E00008"))
        val blue = hex("#6060C0")
        val picked = previewColors(reds + blue, 2)
        assertEquals(listOf(hex("#FF0000"), blue), picked)
    }

    @Test
    fun neutralsOnlyFillWhenColorfulOnesRunOut() {
        val colors = listOf(hex("#000000"), hex("#FFFFFF"), hex("#3B3D44"), hex("#FF0000"))
        val picked = previewColors(colors, 4)
        assertEquals(hex("#FF0000"), picked.first())
        assertEquals(colors.toSet(), picked.toSet())
    }

    @Test
    fun returnsAllColorsWhenFewerThanCount() {
        val colors = listOf(hex("#FF0000"), hex("#00FF00"))
        assertEquals(colors.toSet(), previewColors(colors, 4).toSet())
        assertEquals(emptyList<Int>(), previewColors(emptyList(), 4))
    }

    @Test
    fun everySeasonPreviewsFourColorfulDistinctColors() {
        for (season in Season.entries) {
            val picked = previewColors(SeasonPalettes.best(season), 4)
            assertEquals("$season", 4, picked.toSet().size)
            assertTrue("$season", picked.all { argbToHct(it).chroma >= 16.0 })
        }
    }

    @Test
    fun trueWinterNoLongerPreviewsNeutrals() {
        val neutrals = SeasonPalettes.best(Season.TRUE_WINTER, PaletteCategory.NEUTRALS)
        val picked = previewColors(SeasonPalettes.best(Season.TRUE_WINTER), 4)
        assertTrue(picked.none { it in neutrals })
    }
}
