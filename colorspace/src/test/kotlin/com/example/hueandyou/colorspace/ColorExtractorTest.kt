package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
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
}
