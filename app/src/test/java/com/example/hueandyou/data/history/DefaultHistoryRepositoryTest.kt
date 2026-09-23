package com.example.hueandyou.data.history

import com.example.hueandyou.colorspace.ColorMatch
import com.example.hueandyou.colorspace.ColorMatchBand
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.data.profile.Profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultHistoryRepositoryTest {

    private lateinit var dao: FakeHistoryDao
    private lateinit var thumbnailStore: FakeThumbnailStore
    private lateinit var repository: HistoryRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        dao = FakeHistoryDao()
        thumbnailStore = FakeThumbnailStore()
        repository = DefaultHistoryRepository(dao, thumbnailStore) { clock }
    }

    private fun autumnProfile() = Profile(
        id = 42L,
        name = "Autumn",
        createdAt = 0L,
        updatedAt = 0L,
        bestColors = listOf(PaletteColor(1L, com.example.hueandyou.data.profile.ColorKind.BEST, 0xFF112233.toInt())),
        avoidColors = listOf(PaletteColor(2L, com.example.hueandyou.data.profile.ColorKind.AVOID, 0xFF445566.toInt())),
    )

    private fun sampleScore() = PaletteScore(
        nearestBest = ColorMatch(0xFF112233.toInt(), 2.0, ColorMatchBand.MATCH),
        nearestAvoid = ColorMatch(0xFF445566.toInt(), 12.0, ColorMatchBand.RELATED),
        closerToAvoid = false,
    )

    @Test
    fun saveClothingResult_usesDefaultTypeAndDateName() = runBlocking {
        val entry = repository.saveClothingResult(
            thumbnailPath = "thumb.jpg",
            calibratedArgb = 0xFF778899.toInt(),
            profile = autumnProfile(),
            score = sampleScore(),
        )

        assertEquals(defaultHistoryEntryName(HistoryEntryType.CLOTHING, clock), entry.name)
        assertEquals(HistoryEntryType.CLOTHING, entry.type)
        assertEquals(clock, entry.createdAt)
    }

    @Test
    fun saveClothingResult_snapshotsProfileAndScore() = runBlocking {
        val profile = autumnProfile()
        val score = sampleScore()

        val entry = repository.saveClothingResult(
            thumbnailPath = "thumb.jpg",
            calibratedArgb = 0xFF778899.toInt(),
            profile = profile,
            score = score,
        )

        assertEquals(profile.id, entry.profileId)
        assertEquals(profile.name, entry.profileName)
        assertEquals(listOf(0xFF112233.toInt()), entry.bestColorsArgb)
        assertEquals(listOf(0xFF445566.toInt()), entry.avoidColorsArgb)
        assertEquals(score, entry.score)
    }

    @Test
    fun saveClothingResult_withNoProfile_storesNullSnapshotAndEmptyScore() = runBlocking {
        val score = PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false)

        val entry = repository.saveClothingResult(
            thumbnailPath = "thumb.jpg",
            calibratedArgb = 0xFF778899.toInt(),
            profile = null,
            score = score,
        )

        assertNull(entry.profileId)
        assertNull(entry.profileName)
        assertTrue(entry.bestColorsArgb.isEmpty())
        assertTrue(entry.avoidColorsArgb.isEmpty())
        assertEquals(score, entry.score)
    }

    @Test
    fun observeEntries_returnsNewestFirst() = runBlocking {
        repository.saveClothingResult("a.jpg", 0xFF111111.toInt(), null, PaletteScore(null, null, false))
        clock = 2_000L
        repository.saveClothingResult("b.jpg", 0xFF222222.toInt(), null, PaletteScore(null, null, false))

        val entries = repository.observeEntries().first()

        assertEquals(listOf("b.jpg", "a.jpg"), entries.map { it.thumbnailPath })
    }

    @Test
    fun renameEntry_updatesNamePersistently() = runBlocking {
        val entry = repository.saveClothingResult(
            "a.jpg", 0xFF111111.toInt(), null, PaletteScore(null, null, false)
        )

        repository.renameEntry(entry.id, "My favorite outfit")

        val updated = repository.observeEntry(entry.id).first()
        assertEquals("My favorite outfit", updated?.name)
    }

    @Test
    fun saveClothingResult_capturesSnapshotIndependentOfLaterProfileChanges() = runBlocking {
        val profile = autumnProfile()

        val entry = repository.saveClothingResult(
            "a.jpg", 0xFF778899.toInt(), profile, sampleScore()
        )

        // Simulate the profile being renamed/recolored elsewhere after the snapshot was saved.
        val renamedProfile = profile.copy(name = "Deep Autumn", bestColors = emptyList())

        val stored = repository.observeEntry(entry.id).first()!!
        assertEquals("Autumn", stored.profileName)
        assertEquals(listOf(0xFF112233.toInt()), stored.bestColorsArgb)
        // The renamed profile is a distinct object; the stored snapshot never reads from it.
        assertEquals("Deep Autumn", renamedProfile.name)
    }

    @Test
    fun deleteEntry_removesRowAndThumbnailFile() = runBlocking {
        val entry = repository.saveClothingResult(
            "a.jpg", 0xFF111111.toInt(), null, PaletteScore(null, null, false)
        )

        repository.deleteEntry(entry.id)

        assertNull(repository.observeEntry(entry.id).first())
        assertEquals(listOf("a.jpg"), thumbnailStore.deletedPaths)
    }

    @Test
    fun deleteEntry_withUnknownId_doesNothing() = runBlocking {
        repository.deleteEntry(999L)

        assertTrue(thumbnailStore.deletedPaths.isEmpty())
    }

    @Test
    fun saveObjectResult_usesDefaultTypeAndDateNameAndStoresInputColors() = runBlocking {
        val colors = listOf(0xFF112233.toInt(), 0xFF445566.toInt())

        val entry = repository.saveObjectResult(
            thumbnailPath = "thumb.jpg",
            inputColorsArgb = colors,
            wheel = HarmonyWheel.PERCEPTUAL,
            balance = HarmonyBalance.FAITHFUL,
        )

        assertEquals(defaultHistoryEntryName(HistoryEntryType.OBJECT, clock), entry.name)
        assertEquals(HistoryEntryType.OBJECT, entry.type)
        assertEquals(colors, entry.inputColorsArgb)
        assertEquals(HarmonyWheel.PERCEPTUAL, entry.wheel)
        assertEquals(HarmonyBalance.FAITHFUL, entry.balance)
    }

    @Test
    fun saveObjectResult_roundTripsThroughStorage() = runBlocking {
        val colors = listOf(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt())

        val entry = repository.saveObjectResult(
            thumbnailPath = "thumb.jpg",
            inputColorsArgb = colors,
            wheel = HarmonyWheel.PERCEPTUAL,
            balance = HarmonyBalance.FAITHFUL,
        )

        val stored = repository.observeEntry(entry.id).first()!!
        assertEquals(colors, stored.inputColorsArgb)
        assertEquals(HarmonyWheel.PERCEPTUAL, stored.wheel)
        assertEquals(HarmonyBalance.FAITHFUL, stored.balance)
    }
}

private class FakeThumbnailStore : com.example.hueandyou.data.history.ThumbnailStore {
    val deletedPaths = mutableListOf<String>()

    override suspend fun save(bitmap: android.graphics.Bitmap): String =
        throw UnsupportedOperationException("not used by this test")

    override suspend fun delete(path: String) {
        deletedPaths += path
    }

    override suspend fun readBytes(path: String): ByteArray =
        throw UnsupportedOperationException("not used by this test")

    override suspend fun writeBytes(bytes: ByteArray): String =
        throw UnsupportedOperationException("not used by this test")
}
