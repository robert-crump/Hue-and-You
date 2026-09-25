package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorExtractorTest {

    @Test
    fun largestShareColorInCenterBoxIsPicked() {
        val width = 100
        val height = 100
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        // Center 40x40 box spans x in [30, 70) - mostly red (x < 60) with a thin blue strip.
        val pixels = IntArrayPixelSource(
            width,
            height,
            IntArray(width * height) { index -> if (index % width < 60) red else blue },
        )

        val result = ColorExtractor.extractMainColor(pixels)

        assertEquals(red, result)
    }

    @Test
    fun colorsOutsideCenterBoxAreIgnored() {
        val width = 100
        val height = 100
        val green = 0xFF00FF00.toInt()
        val white = 0xFFFFFFFF.toInt()
        // The center 40x40 box (x, y in [30, 70)) is entirely green; everything else is white.
        val pixels = IntArrayPixelSource(
            width,
            height,
            IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                if (x in 30 until 70 && y in 30 until 70) green else white
            },
        )

        val result = ColorExtractor.extractMainColor(pixels)

        assertEquals(green, result)
    }

    @Test
    fun explicitRegionOverridesCenterBox() {
        val width = 10
        val height = 10
        val purple = 0xFF800080.toInt()
        val yellow = 0xFFFFFF00.toInt()
        val pixels = IntArrayPixelSource(
            width,
            height,
            IntArray(width * height) { index -> if (index % width < 5) purple else yellow },
        )

        val result = ColorExtractor.extractMainColor(pixels, region = RectRegion(5, 0, 10, 10))

        assertEquals(yellow, result)
    }

    @Test
    fun singleColorImageReturnsThatColor() {
        val width = 30
        val height = 30
        val teal = 0xFF008080.toInt()
        val pixels = IntArrayPixelSource(width, height, IntArray(width * height) { teal })

        val result = ColorExtractor.extractMainColor(pixels)

        assertEquals(teal, result)
    }

    @Test
    fun extractCandidates_hasNoMinimumShareFilter() {
        val width = 100
        val height = 100
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        // Center box is x, y in [30, 70): a thin two-row blue strip (5% share) among red.
        val pixels = IntArrayPixelSource(
            width,
            height,
            IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                if (x in 30 until 70 && y in 30 until 70) {
                    if (y in 30 until 32) blue else red
                } else {
                    red
                }
            },
        )

        val candidates = ColorExtractor.extractCandidates(pixels)

        assertEquals(listOf(red, blue), candidates.map { it.argb })
        assertTrue(candidates.last().share < 0.1)
    }

    @Test
    fun extractColorAtPoint_onlySamplesWithinTheCircle() {
        val width = 40
        val height = 40
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        // A red disc of radius 3 around (20, 20), blue everywhere else.
        val pixels = IntArrayPixelSource(
            width,
            height,
            IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                val dx = x - 20
                val dy = y - 20
                if (dx * dx + dy * dy <= 9) red else blue
            },
        )

        val result = ColorExtractor.extractColorAtPoint(pixels, x = 20, y = 20, radiusFraction = 3.0 / 40.0)

        assertEquals(red, result)
    }

    @Test
    fun extractColorAtPoint_clampsToImageBounds() {
        val width = 10
        val height = 10
        val green = 0xFF00FF00.toInt()
        val pixels = IntArrayPixelSource(width, height, IntArray(width * height) { green })

        val result = ColorExtractor.extractColorAtPoint(pixels, x = 0, y = 0, radiusFraction = 0.5)

        assertEquals(green, result)
    }
}
