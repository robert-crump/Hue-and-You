package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwatchExtractorTest {
    private val swatchSize = 20
    private val grid = 5

    // Well-separated colors: red and green step by 60 across the grid, blue is scrambled.
    private val swatchColors = List(grid * grid) { i ->
        val r = (i % grid) * 60 + 8
        val g = (i / grid) * 60 + 8
        val b = (i * 97) % 240 + 8
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun gridPixels(): IntArrayPixelSource {
        val size = swatchSize * grid
        return IntArrayPixelSource(
            size,
            size,
            IntArray(size * size) { index ->
                val x = index % size
                val y = index / size
                swatchColors[(y / swatchSize) * grid + (x / swatchSize)]
            },
        )
    }

    @Test
    fun largeSwatchGridReturnsOneColorPerSwatch() {
        val result = SwatchExtractor.extract(gridPixels())

        assertEquals(swatchColors.toSet(), result.map { it.argb }.toSet())
    }

    @Test
    fun partiallyCoveredEdgeSwatchesStillYieldTheirColor() {
        val size = swatchSize * grid
        val region = RectRegion(left = 8, top = 8, right = size - 8, bottom = size - 8)

        val result = SwatchExtractor.extract(gridPixels(), region = region)

        assertEquals(swatchColors.toSet(), result.map { it.argb }.toSet())
    }

    @Test
    fun antiAliasedEdgePixelsFoldIntoNearbySwatch() {
        val red = 0xFFFF0000.toInt()
        val nearRed = 0xFFFA0303.toInt()
        val blue = 0xFF0000FF.toInt()
        val pixels = IntArrayPixelSource(4, 1, intArrayOf(red, red, nearRed, blue))

        val result = SwatchExtractor.extract(pixels, minShare = 0.0)

        assertEquals(listOf(red, blue), result.map { it.argb })
        assertTrue(result[0].share > 0.7)
    }
}
