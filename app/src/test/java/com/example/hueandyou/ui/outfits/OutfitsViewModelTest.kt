package com.example.hueandyou.ui.outfits

import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.data.outfit.FakeOutfitRepository
import com.example.hueandyou.data.outfit.Outfit
import com.example.hueandyou.data.profile.FakeProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OutfitsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val autumn = profile(1L, "Autumn", best = RED, avoid = GREEN)
    private val winter = profile(2L, "Winter", best = BLUE, avoid = RED)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listsOutfitsWithTheirColors_ratedAgainstTheLastUsedProfile_andFlagsOnesTooSmallToRate() = runTest(dispatcher) {
        val history = FakeHistoryRepository(clothing(1L, RED), clothing(2L, GREEN), clothing(3L, BLUE))
        val outfits = FakeOutfitRepository(
            Outfit(10L, "Old", 0L, listOf(1L, 3L)),
            Outfit(11L, "Lonely", 0L, listOf(2L)),
        )
        val settings = FakeSettingsRepository(lastUsedClothingProfileId = 2L, wheel = HarmonyWheel.SCREEN)
        val model = OutfitsViewModel(
            outfits,
            history,
            FakeProfileRepository().apply { profiles.value = listOf(autumn, winter) },
            settings,
        )
        val job = launch { model.uiState.collect {} }
        runCurrent()

        val rows = model.uiState.value.outfits
        assertEquals(listOf("Lonely", "Old"), rows.map { it.name })
        assertNull(rows[0].rating)
        assertEquals(listOf(RED, BLUE), rows[1].colorsArgb)
        assertEquals(rateOutfit(listOf(RED, BLUE), winter, HarmonyWheel.SCREEN), rows[1].rating)

        // A deleted entry leaves the outfit with too few items to rate.
        history.deleteEntry(3L)
        runCurrent()
        assertNull(model.uiState.value.outfits[1].rating)

        job.cancel()
    }
}
