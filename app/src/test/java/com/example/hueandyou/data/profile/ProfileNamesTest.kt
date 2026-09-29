package com.example.hueandyou.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNamesTest {

    @Test
    fun isProfileNameTaken_ignoresCaseAndSurroundingSpaces() {
        assertTrue(isProfileNameTaken("  true winter ", listOf("True Winter")))
        assertFalse(isProfileNameTaken("True Winter 2", listOf("True Winter")))
        assertFalse(isProfileNameTaken("True Winter", emptyList()))
    }

    @Test
    fun uniqueProfileName_countsUpFromTwo() {
        assertEquals("Soft Autumn", uniqueProfileName("Soft Autumn", listOf("Deep Winter")))
        assertEquals("Soft Autumn 2", uniqueProfileName("Soft Autumn", listOf("Soft Autumn")))
        assertEquals("Soft Autumn 2", uniqueProfileName("Soft Autumn", listOf("Soft Autumn", "Soft Autumn 3")))
    }

    @Test
    fun uniqueProfileName_treatsOtherCaseAsTaken() {
        assertEquals("Soft Autumn 3", uniqueProfileName("Soft Autumn", listOf("soft autumn", "SOFT AUTUMN 2")))
    }
}
