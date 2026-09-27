package com.example.hueandyou.ui.paletteimport

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSwatchSelectionTest {

    private fun swatches(count: Int) =
        (0 until count).map { ImportSwatch(id = it, argb = it, share = 1.0 / (it + 1)) }

    @Test
    fun preselectWithinSlots_selectsOnlyTheMostDominantSwatches() {
        val result = preselectWithinSlots(swatches(5), slots = 2)

        assertEquals(listOf(true, true, false, false, false), result.map { it.selected })
    }

    @Test
    fun preselectWithinSlots_withNoSlots_selectsNothing() {
        val result = preselectWithinSlots(swatches(3), slots = 0)

        assertEquals(listOf(false, false, false), result.map { it.selected })
    }

    @Test
    fun toggleWithinSlots_whenFull_doesNotSelectMore() {
        val full = preselectWithinSlots(swatches(3), slots = 2)

        val result = toggleWithinSlots(full, swatchId = 2, slots = 2)

        assertEquals(listOf(true, true, false), result.map { it.selected })
    }

    @Test
    fun toggleWithinSlots_whenFull_stillAllowsDeselecting() {
        val full = preselectWithinSlots(swatches(3), slots = 2)

        val result = toggleWithinSlots(full, swatchId = 0, slots = 2)

        assertEquals(listOf(false, true, false), result.map { it.selected })
    }

    @Test
    fun toggleWithinSlots_withRoom_selects() {
        val partial = preselectWithinSlots(swatches(3), slots = 1)

        val result = toggleWithinSlots(partial, swatchId = 2, slots = 2)

        assertEquals(listOf(true, false, true), result.map { it.selected })
    }
}
