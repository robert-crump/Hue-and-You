package com.example.hueandyou.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class HexColorDialogTest {

    @Test
    fun sanitizeHexInput_uppercasesAndDropsNonHexCharacters() {
        assertEquals("3A7DFF", sanitizeHexInput("#3a7dff"))
        assertEquals("ABC", sanitizeHexInput("a-b g c"))
    }

    @Test
    fun sanitizeHexInput_capsAtSixDigits() {
        assertEquals("123456", sanitizeHexInput("12345678"))
    }
}
