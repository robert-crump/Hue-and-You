package com.example.hueandyou.colorspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexColorTest {

    @Test
    fun parsesWithHash() {
        assertEquals(0xFF1A2B3C.toInt(), parseHexColor("#1A2B3C"))
    }

    @Test
    fun parsesWithoutHash() {
        assertEquals(0xFF1A2B3C.toInt(), parseHexColor("1A2B3C"))
    }

    @Test
    fun parsesLowercase() {
        assertEquals(0xFF1A2B3C.toInt(), parseHexColor("#1a2b3c"))
    }

    @Test
    fun parsesMixedCase() {
        assertEquals(0xFF1A2B3C.toInt(), parseHexColor("#1a2B3c"))
    }

    @Test
    fun rejectsTooShort() {
        assertNull(parseHexColor("#1A2B3"))
    }

    @Test
    fun rejectsTooLong() {
        assertNull(parseHexColor("#1A2B3C4"))
    }

    @Test
    fun rejectsNonHexCharacters() {
        assertNull(parseHexColor("#GGGGGG"))
    }

    @Test
    fun rejectsBlank() {
        assertNull(parseHexColor(""))
    }

    @Test
    fun rejectsDoubleHash() {
        assertNull(parseHexColor("##1A2B3C"))
    }

    @Test
    fun formatsAsUppercaseHashRrggbb() {
        assertEquals("#1A2B3C", formatHexColor(0xFF1A2B3C.toInt()))
    }

    @Test
    fun formatIgnoresAlpha() {
        assertEquals("#1A2B3C", formatHexColor(0x001A2B3C))
    }

    @Test
    fun parseFormatRoundTrips() {
        val hex = "#4C7A9E"
        assertEquals(hex, formatHexColor(parseHexColor(hex)!!))
    }
}
