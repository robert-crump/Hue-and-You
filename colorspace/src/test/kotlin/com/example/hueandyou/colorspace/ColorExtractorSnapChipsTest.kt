package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorExtractorSnapChipsTest {

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun replacesTheNearestChipWithTheSavedColor() {
        val nearlyGreen = 0xFF03FC02.toInt()

        val chips = ColorExtractor.snapChipsTo(listOf(red, green, blue), nearlyGreen)

        assertEquals(listOf(red, nearlyGreen, blue), chips)
    }

    @Test
    fun leavesChipsAloneWhenTheSavedColorIsAlreadyOne() {
        assertEquals(listOf(red, green, blue), ColorExtractor.snapChipsTo(listOf(red, green, blue), green))
    }

    @Test
    fun leavesChipsAloneWhenNoChipIsClose() {
        val gray = 0xFF808080.toInt()

        assertEquals(listOf(red, green, blue), ColorExtractor.snapChipsTo(listOf(red, green, blue), gray))
    }
}
