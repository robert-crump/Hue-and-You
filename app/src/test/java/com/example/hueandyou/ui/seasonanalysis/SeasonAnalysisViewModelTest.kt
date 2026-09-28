package com.example.hueandyou.ui.seasonanalysis

import com.example.hueandyou.colorspace.IntArrayPixelSource
import com.example.hueandyou.colorspace.Season
import com.example.hueandyou.colorspace.SeasonPalettes
import com.example.hueandyou.data.profile.FakeProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Fair warm skin, golden blonde hair, light blue eyes.
private const val SKIN = 0xFFF2C9A6.toInt()
private const val HAIR = 0xFFB8985A.toInt()
private const val EYES = 0xFF7FA3C8.toInt()

private const val STRIPE_WIDTH = 100
private const val PHOTO_HEIGHT = 100

/** Tap points in the middle of the skin, hair and eye stripes. */
private val SKIN_TAP = 50 to 50
private val HAIR_TAP = 150 to 50
private val EYES_TAP = 250 to 50

/** A photo of three vertical stripes: skin, hair, eyes. */
private fun stripedPhoto(): IntArrayPixelSource {
    val colors = listOf(SKIN, HAIR, EYES)
    val width = STRIPE_WIDTH * colors.size
    val pixels = IntArray(width * PHOTO_HEIGHT) { index -> colors[(index % width) / STRIPE_WIDTH] }
    return IntArrayPixelSource(width, PHOTO_HEIGHT, pixels)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SeasonAnalysisViewModelTest {

    private lateinit var repository: FakeProfileRepository
    private lateinit var viewModel: SeasonAnalysisViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeProfileRepository()
        viewModel = SeasonAnalysisViewModel(repository, seasonName = { it.name })
        viewModel.startPicking(stripedPhoto())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun tap(point: Pair<Int, Int>) = viewModel.onPhotoTap(point.first, point.second)

    private fun picking() = viewModel.uiState.value as SeasonAnalysisUiState.PickingColors

    private fun result() = viewModel.uiState.value as SeasonAnalysisUiState.ShowingResult

    private fun pickAll() {
        tap(SKIN_TAP)
        tap(HAIR_TAP)
        tap(EYES_TAP)
    }

    @Test
    fun startsOnSkinWithNothingPicked() {
        assertEquals(SeasonFeature.SKIN, picking().active)
        assertTrue(picking().picks.isEmpty())
    }

    @Test
    fun tapSequence_picksSkinHairEyesAndAdvances() {
        tap(SKIN_TAP)
        assertEquals(SeasonFeature.HAIR, picking().active)
        tap(HAIR_TAP)
        assertEquals(SeasonFeature.EYES, picking().active)
        tap(EYES_TAP)

        val state = picking()
        assertNull(state.active)
        assertEquals(SKIN, state.picks.getValue(SeasonFeature.SKIN).argb)
        assertEquals(HAIR, state.picks.getValue(SeasonFeature.HAIR).argb)
        assertEquals(EYES, state.picks.getValue(SeasonFeature.EYES).argb)
        assertEquals(FeaturePick(HAIR, HAIR_TAP.first, HAIR_TAP.second), state.picks[SeasonFeature.HAIR])
    }

    @Test
    fun tapWithAllPicksSetAndNoActiveChip_changesNothing() {
        pickAll()
        val before = picking()

        tap(SKIN_TAP)

        assertEquals(before, picking())
    }

    @Test
    fun rePick_replacesThatPickOnly() {
        pickAll()

        viewModel.selectFeature(SeasonFeature.HAIR)
        assertEquals(SeasonFeature.HAIR, picking().active)
        tap(EYES_TAP)

        val state = picking()
        assertEquals(EYES, state.picks.getValue(SeasonFeature.HAIR).argb)
        assertEquals(SKIN, state.picks.getValue(SeasonFeature.SKIN).argb)
        assertNull(state.active)
    }

    @Test
    fun rePickBeforeAllSet_advancesToNextMissing() {
        tap(SKIN_TAP)
        viewModel.selectFeature(SeasonFeature.SKIN)

        tap(HAIR_TAP)

        assertEquals(HAIR, picking().picks.getValue(SeasonFeature.SKIN).argb)
        assertEquals(SeasonFeature.HAIR, picking().active)
    }

    @Test
    fun pickingEyesFirst_wrapsAroundToSkin() {
        viewModel.selectFeature(SeasonFeature.EYES)

        tap(EYES_TAP)

        assertEquals(SeasonFeature.SKIN, picking().active)
    }

    @Test
    fun tapOutsidePhoto_isIgnored() {
        viewModel.onPhotoTap(-1, 50)
        viewModel.onPhotoTap(50, PHOTO_HEIGHT)

        assertTrue(picking().picks.isEmpty())
    }

    @Test
    fun next_enabledOnlyOnceAllThreeAreSet() {
        tap(SKIN_TAP)
        tap(HAIR_TAP)
        assertFalse(picking().canContinue)
        viewModel.showResult()
        assertTrue(viewModel.uiState.value is SeasonAnalysisUiState.PickingColors)

        tap(EYES_TAP)
        assertTrue(picking().canContinue)
    }

    @Test
    fun showResult_listsTopThreeWithTheBestSelected() {
        pickAll()

        viewModel.showResult()

        val state = result()
        assertEquals(SEASON_RESULT_COUNT, state.topMatches.size)
        assertEquals(state.topMatches.sortedByDescending { it.fit }, state.topMatches)
        assertEquals(state.topMatches.first().season, state.selected)
        assertEquals(SeasonPalettes.best(state.selected), state.bestColors)
        assertEquals(SeasonPalettes.avoid(state.selected), state.avoidColors)
    }

    @Test
    fun selectSeason_selectsOnlyListedSeasons() {
        pickAll()
        viewModel.showResult()
        val second = result().topMatches[1].season

        viewModel.selectSeason(second)
        assertEquals(second, result().selected)

        val unlisted = Season.entries.first { season ->
            result().topMatches.none { it.season == season }
        }
        viewModel.selectSeason(unlisted)
        assertEquals(second, result().selected)
    }

    @Test
    fun backFromResult_returnsToPickingWithPicksKept() {
        pickAll()
        viewModel.showResult()

        assertTrue(viewModel.back())

        assertEquals(3, picking().picks.size)
        assertTrue(picking().canContinue)
    }

    @Test
    fun backFromPicking_returnsToPhotoStep_thenLeaves() {
        assertTrue(viewModel.back())
        assertEquals(SeasonAnalysisUiState.PickingPhoto, viewModel.uiState.value)

        assertFalse(viewModel.back())
    }

    @Test
    fun nothingIsCreatedBeforeConfirm() {
        pickAll()
        viewModel.showResult()

        assertTrue(repository.profiles.value.isEmpty())
    }

    @Test
    fun createProfile_savesSelectedSeasonsPaletteInOrder() {
        pickAll()
        viewModel.showResult()
        val selected = result().topMatches[1].season
        viewModel.selectSeason(selected)

        viewModel.createProfile()

        val profile = repository.profiles.value.single()
        assertEquals(selected.name, profile.name)
        assertEquals(SeasonPalettes.best(selected), profile.bestColors.map { it.argb })
        assertEquals(SeasonPalettes.avoid(selected), profile.avoidColors.map { it.argb })
        assertEquals(SeasonAnalysisUiState.Done(profile.id), viewModel.uiState.value)
    }

    @Test
    fun createProfile_dedupesTheName() {
        pickAll()
        viewModel.showResult()
        val name = result().selected.name
        repository.createProfileBlocking(name)
        repository.createProfileBlocking("$name 2")

        viewModel.createProfile()

        assertEquals("$name 3", repository.profiles.value.last().name)
    }

    @Test
    fun createProfileTwice_createsOneProfile() {
        pickAll()
        viewModel.showResult()

        viewModel.createProfile()
        viewModel.createProfile()

        assertEquals(1, repository.profiles.value.size)
    }

    @Test
    fun uniqueProfileName_countsUpFromTwo() {
        assertEquals("Soft Autumn", uniqueProfileName("Soft Autumn", listOf("Deep Winter")))
        assertEquals("Soft Autumn 2", uniqueProfileName("Soft Autumn", listOf("Soft Autumn")))
        assertEquals("Soft Autumn 2", uniqueProfileName("Soft Autumn", listOf("Soft Autumn", "Soft Autumn 3")))
    }

    @Test
    fun nextMissingFeature_wrapsAndEndsWithNull() {
        val skinPick = FeaturePick(SKIN, 0, 0)
        assertEquals(SeasonFeature.HAIR, nextMissingFeature(emptyMap(), after = SeasonFeature.SKIN))
        assertEquals(SeasonFeature.SKIN, nextMissingFeature(emptyMap(), after = SeasonFeature.EYES))
        assertEquals(
            SeasonFeature.EYES,
            nextMissingFeature(mapOf(SeasonFeature.SKIN to skinPick), after = SeasonFeature.HAIR),
        )
        assertNull(
            nextMissingFeature(
                SeasonFeature.entries.associateWith { skinPick },
                after = SeasonFeature.SKIN,
            )
        )
    }
}
