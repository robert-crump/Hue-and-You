package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorExtractorTest {

    @Test
    fun twoSolidRegionsReturnBothColorsWithCorrectShares() {
        val width = 100
        val height = 100
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val pixels = IntArrayPixelSource(
            width,
            height,
            IntArray(width * height) { index -> if (index % width < width / 2) red else blue },
        )

        val result = ColorExtractor.extract(pixels)

        assertEquals(2, result.colors.size)
        val byArgb = result.colors.associateBy { it.argb }
        assertTrue("Expected red in $result", byArgb.containsKey(red))
        assertTrue("Expected blue in $result", byArgb.containsKey(blue))
        assertEquals(0.5, byArgb.getValue(red).share, 0.02)
        assertEquals(0.5, byArgb.getValue(blue).share, 0.02)
    }

    @Test
    fun excludedRegionNeverAppearsInResult() {
        val width = 100
        val height = 100
        val green = 0xFF00FF00.toInt()
        val white = 0xFFFFFFFF.toInt()
        val exclusion = CircleRegion(centerX = 50, centerY = 50, radius = 20)
        val pixels = IntArrayPixelSource(
            width,
            height,
            IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                if (exclusion.contains(x, y)) white else green
            },
        )

        val result = ColorExtractor.extract(pixels, exclusion = exclusion)

        assertTrue(result.colors.none { it.argb == white })
        assertEquals(listOf(green), result.colors.map { it.argb })
    }

    @Test
    fun singleDominantColorIsFlaggedAsClearlyDominant() {
        val width = 30
        val height = 30
        val purple = 0xFF800080.toInt()
        val pixels = IntArrayPixelSource(width, height, IntArray(width * height) { purple })

        val result = ColorExtractor.extract(pixels)

        assertTrue(result.isClearlyDominant)
        assertEquals(1, result.colors.size)
        assertEquals(purple, result.colors.first().argb)
        assertEquals(1.0, result.colors.first().share, 1e-9)
    }

    @Test
    fun notClearlyDominantWhenTopShareBelowThreshold() {
        val width = 100
        val height = 100
        val a = 0xFFFF0000.toInt()
        val b = 0xFF00FF00.toInt()
        val pixels = IntArrayPixelSource(
            width,
            height,
            IntArray(width * height) { index -> if (index % width < width / 2) a else b },
        )

        val result = ColorExtractor.extract(pixels)

        assertFalse(result.isClearlyDominant)
    }
}
