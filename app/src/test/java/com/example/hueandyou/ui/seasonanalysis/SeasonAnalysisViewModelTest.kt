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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Fair warm skin, golden blonde hair, light blue eyes.
private const val SKIN = 0xFFF2C9A6.toInt()
private const val HAIR = 0xFFB8985A.toInt()
private const val EYES = 0xFF7FA3C8.toInt()

private const val STRIPE_WIDTH = 100
private const val PHOTO_HEIGHT = 100

/** Marker spots in the middle of the skin, hair and eye stripes. */
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun capturing() = viewModel.uiState.value as SeasonAnalysisUiState.Capturing

    private fun placing() = viewModel.uiState.value as SeasonAnalysisUiState.Placing

    private fun result() = viewModel.uiState.value as SeasonAnalysisUiState.ShowingResult

    /** Stands in for taking [capturing]'s photo: opens its marker step on the striped photo. */
    private fun takePhoto() {
        val state = capturing()
        viewModel.startPlacing(state.feature, state.picks, stripedPhoto())
    }

    private fun moveMarker(point: Pair<Int, Int>) = viewModel.moveMarker(point.first, point.second)

    /** Takes the current step's photo, puts the marker on [point] and presses Next. */
    private fun completeStep(point: Pair<Int, Int>) {
        takePhoto()
        moveMarker(point)
        viewModel.next()
    }

    /** Runs all three steps and lands on the result. */
    private fun pickAll() {
        completeStep(SKIN_TAP)
        completeStep(HAIR_TAP)
        completeStep(EYES_TAP)
    }

    @Test
    fun startsOnTheSkinCameraWithNothingPicked() {
        assertEquals(SeasonAnalysisUiState.Capturing(SeasonFeature.SKIN, emptyMap()), viewModel.uiState.value)
    }

    @Test
    fun newPhoto_putsTheMarkerInTheMiddleWithItsColor() {
        takePhoto()

        // The middle of the 300 x 100 photo lies in the hair stripe.
        assertEquals(SeasonFeature.SKIN, placing().feature)
        assertEquals(FeaturePick(HAIR, 150, 50), placing().marker)
    }

    @Test
    fun moveMarker_samplesTheNewSpotLive() {
        takePhoto()

        moveMarker(SKIN_TAP)
        assertEquals(FeaturePick(SKIN, SKIN_TAP.first, SKIN_TAP.second), placing().marker)
        moveMarker(EYES_TAP)
        assertEquals(FeaturePick(EYES, EYES_TAP.first, EYES_TAP.second), placing().marker)
        assertTrue(placing().picks.isEmpty())
    }

    @Test
    fun moveMarker_clampsToThePhoto() {
        takePhoto()

        viewModel.moveMarker(-10, PHOTO_HEIGHT + 10)

        assertEquals(FeaturePick(SKIN, 0, PHOTO_HEIGHT - 1), placing().marker)
    }

    @Test
    fun next_keepsTheMarkerAndMovesToTheNextCamera() {
        completeStep(SKIN_TAP)

        assertEquals(SeasonFeature.HAIR, capturing().feature)
        assertEquals(mapOf(SeasonFeature.SKIN to FeaturePick(SKIN, 50, 50)), capturing().picks)

        completeStep(HAIR_TAP)
        assertEquals(SeasonFeature.EYES, capturing().feature)
        assertEquals(HAIR, capturing().picks.getValue(SeasonFeature.HAIR).argb)
    }

    @Test
    fun nextAfterEyes_showsTheResultWithAllThreeColors() {
        pickAll()

        val picks = result().picks
        assertEquals(SKIN, picks.getValue(SeasonFeature.SKIN).argb)
        assertEquals(HAIR, picks.getValue(SeasonFeature.HAIR).argb)
        assertEquals(EYES, picks.getValue(SeasonFeature.EYES).argb)
    }

    @Test
    fun moveMarker_outsideThePlacingStep_isIgnored() {
        viewModel.moveMarker(10, 10)

        assertEquals(SeasonAnalysisUiState.Capturing(), viewModel.uiState.value)
    }

    @Test
    fun result_listsTopThreeWithTheBestSelected() {
        pickAll()

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
    fun backFromResult_reopensTheEyesMarkerWhereItWas() {
        pickAll()

        assertTrue(viewModel.back())

        assertEquals(SeasonFeature.EYES, placing().feature)
        assertEquals(FeaturePick(EYES, EYES_TAP.first, EYES_TAP.second), placing().marker)
        assertEquals(setOf(SeasonFeature.SKIN, SeasonFeature.HAIR), placing().picks.keys)
    }

    @Test
    fun backFromMarker_returnsToThatStepsCamera() {
        completeStep(SKIN_TAP)
        takePhoto()

        assertTrue(viewModel.back())

        assertEquals(SeasonFeature.HAIR, capturing().feature)
        assertEquals(setOf(SeasonFeature.SKIN), capturing().picks.keys)
    }

    @Test
    fun retake_fromMarker_returnsToThatStepsCameraKeepingEarlierPicks() {
        completeStep(SKIN_TAP)
        takePhoto()
        moveMarker(EYES_TAP)

        viewModel.retake()

        assertEquals(SeasonFeature.HAIR, capturing().feature)
        assertEquals(mapOf(SeasonFeature.SKIN to FeaturePick(SKIN, 50, 50)), capturing().picks)
    }

    @Test
    fun retake_outsideThePlacingStep_isIgnored() {
        viewModel.retake()
        assertEquals(SeasonAnalysisUiState.Capturing(), viewModel.uiState.value)

        pickAll()
        val state = result()
        viewModel.retake()
        assertEquals(state, viewModel.uiState.value)
    }

    @Test
    fun backFromHairCamera_reopensTheSkinMarkerWhereItWas() {
        completeStep(SKIN_TAP)

        assertTrue(viewModel.back())

        assertEquals(SeasonFeature.SKIN, placing().feature)
        assertEquals(FeaturePick(SKIN, SKIN_TAP.first, SKIN_TAP.second), placing().marker)
        assertTrue(placing().picks.isEmpty())
    }

    @Test
    fun backFromSkinCamera_leavesTheFlow() {
        assertFalse(viewModel.back())

        takePhoto()
        assertTrue(viewModel.back())
        assertFalse(viewModel.back())
    }

    @Test
    fun nothingIsCreatedBeforeConfirm() {
        pickAll()

        assertTrue(repository.profiles.value.isEmpty())
    }

    @Test
    fun createProfile_savesSelectedSeasonsPaletteInOrder() {
        pickAll()
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
        val name = result().selected.name
        repository.createProfileBlocking(name)
        repository.createProfileBlocking("$name 2")

        viewModel.createProfile()

        assertEquals("$name 3", repository.profiles.value.last().name)
    }

    @Test
    fun createProfileTwice_createsOneProfile() {
        pickAll()

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
}
