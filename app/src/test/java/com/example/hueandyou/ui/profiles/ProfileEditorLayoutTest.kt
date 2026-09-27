package com.example.hueandyou.ui.profiles

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditorLayoutTest {

    @Test
    fun worstCaseCircleSize_typicalPhone_fitsTenRowsWithinClamp() {
        val size = worstCaseCircleSize(maxWidth = 360.dp, maxHeight = 666.dp)

        assertTrue(size in 32.dp..56.dp)
        assertTrue(size < 40.dp)
    }

    @Test
    fun worstCaseCircleSize_tinyScreen_clampsToMinimum() {
        assertEquals(32.dp, worstCaseCircleSize(maxWidth = 320.dp, maxHeight = 400.dp))
    }

    @Test
    fun worstCaseCircleSize_tallScreen_clampsToMaximum() {
        assertEquals(56.dp, worstCaseCircleSize(maxWidth = 600.dp, maxHeight = 2000.dp))
    }

    @Test
    fun worstCaseCircleSize_narrowScreen_isBoundByColumnWidth() {
        assertEquals(40.dp, worstCaseCircleSize(maxWidth = 272.dp, maxHeight = 2000.dp))
    }
}
