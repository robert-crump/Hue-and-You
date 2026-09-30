package com.example.hueandyou.ui.outfits

import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.history.ClothingCategory
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.NewClothingResult
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.settings.HarmonyDefaults
import com.example.hueandyou.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal const val RED = 0xFFFF0000.toInt()
internal const val GREEN = 0xFF00FF00.toInt()
internal const val BLUE = 0xFF0000FF.toInt()

internal fun clothing(
    id: Long,
    argb: Int,
    name: String = "Item $id",
    inWardrobe: Boolean = true,
    category: ClothingCategory? = null,
    type: HistoryEntryType = HistoryEntryType.CLOTHING,
) = HistoryEntry(
    id = id,
    type = type,
    name = name,
    createdAt = id,
    thumbnailPath = "$id.jpg",
    calibratedArgb = argb,
    profileId = null,
    profileName = null,
    bestColorsArgb = emptyList(),
    avoidColorsArgb = emptyList(),
    score = PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false),
    inWardrobe = inWardrobe,
    category = category,
)

internal fun profile(id: Long, name: String, best: Int, avoid: Int) = Profile(
    id = id,
    name = name,
    createdAt = 0L,
    updatedAt = 0L,
    bestColors = listOf(PaletteColor(id * 10 + 1, ColorKind.BEST, best)),
    avoidColors = listOf(PaletteColor(id * 10 + 2, ColorKind.AVOID, avoid)),
)

/** Only [observeEntries] and [deleteEntry] are used by the outfit ViewModels. */
internal class FakeHistoryRepository(vararg entries: HistoryEntry) : HistoryRepository {
    val entries = MutableStateFlow(entries.sortedByDescending { it.createdAt })

    override fun observeEntries(): Flow<List<HistoryEntry>> = entries

    override fun observeEntry(entryId: Long): Flow<HistoryEntry?> = entries.map { list -> list.find { it.id == entryId } }

    override suspend fun deleteEntry(entryId: Long) {
        entries.value = entries.value.filterNot { it.id == entryId }
    }

    override suspend fun saveClothingResult(
        thumbnailPath: String,
        calibratedArgb: Int,
        profile: Profile?,
        score: PaletteScore,
        chipColorsArgb: List<Int>,
        name: String,
        inWardrobe: Boolean,
        category: ClothingCategory?,
    ): HistoryEntry = throw UnsupportedOperationException("not used by these tests")

    override suspend fun saveClothingResults(results: List<NewClothingResult>) =
        throw UnsupportedOperationException("not used by these tests")

    override suspend fun saveObjectResult(
        thumbnailPath: String,
        inputColorsArgb: List<Int>,
        wheel: HarmonyWheel,
        balance: HarmonyBalance,
        chipColorsArgb: List<Int>,
        name: String,
    ): HistoryEntry = throw UnsupportedOperationException("not used by these tests")

    override suspend fun renameEntry(entryId: Long, name: String) =
        throw UnsupportedOperationException("not used by these tests")

    override suspend fun updateObjectPick(entryId: Long, argb: Int, sampleX: Double?, sampleY: Double?) =
        throw UnsupportedOperationException("not used by these tests")

    override suspend fun updateClothingPick(entryId: Long, argb: Int, score: PaletteScore, sampleX: Double?, sampleY: Double?) =
        throw UnsupportedOperationException("not used by these tests")

    override suspend fun updateClothingProfile(entryId: Long, profile: Profile, score: PaletteScore) =
        throw UnsupportedOperationException("not used by these tests")

    override suspend fun updateChipColors(entryId: Long, chipColorsArgb: List<Int>) =
        throw UnsupportedOperationException("not used by these tests")

    override suspend fun updateWardrobeDetails(entryId: Long, inWardrobe: Boolean, category: ClothingCategory?) =
        throw UnsupportedOperationException("not used by these tests")
}

internal class FakeSettingsRepository(lastUsedClothingProfileId: Long?, wheel: HarmonyWheel = HarmonyWheel.PERCEPTUAL) :
    SettingsRepository {
    val lastUsedClothingProfileId = MutableStateFlow(lastUsedClothingProfileId)
    val defaults = MutableStateFlow(HarmonyDefaults(wheel = wheel))

    override fun observeDefaults(): Flow<HarmonyDefaults> = defaults

    override suspend fun setDefaultWheel(wheel: HarmonyWheel) {
        defaults.value = defaults.value.copy(wheel = wheel)
    }

    override suspend fun setDefaultBalance(balance: HarmonyBalance) {
        defaults.value = defaults.value.copy(balance = balance)
    }

    override fun observeLastUsedClothingProfileId(): Flow<Long?> = lastUsedClothingProfileId

    override suspend fun setLastUsedClothingProfileId(profileId: Long) {
        lastUsedClothingProfileId.value = profileId
    }
}
