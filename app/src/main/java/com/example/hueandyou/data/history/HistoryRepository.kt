package com.example.hueandyou.data.history

import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.profile.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HistoryRepository {
    fun observeEntries(): Flow<List<HistoryEntry>>
    fun observeEntry(entryId: Long): Flow<HistoryEntry?>

    /** Saves a Rate Clothing result as a new history entry and returns it (with its assigned id). */
    suspend fun saveClothingResult(
        thumbnailPath: String,
        calibratedArgb: Int,
        profile: Profile?,
        score: PaletteScore
    ): HistoryEntry

    /** Saves a Match Colors for an Object result as a new history entry and returns it. */
    suspend fun saveObjectResult(
        thumbnailPath: String,
        inputColorsArgb: List<Int>,
        wheel: HarmonyWheel,
        balance: HarmonyBalance,
    ): HistoryEntry

    suspend fun renameEntry(entryId: Long, name: String)

    /** Updates an OBJECT entry in place after a re-pick: the new color and its sample location. */
    suspend fun updateObjectPick(entryId: Long, argb: Int, sampleX: Double?, sampleY: Double?)

    /** Updates a CLOTHING entry in place after a re-pick: the new color, its recomputed score, and sample location. */
    suspend fun updateClothingPick(entryId: Long, argb: Int, score: PaletteScore, sampleX: Double?, sampleY: Double?)

    /** Updates a CLOTHING entry in place after switching profiles: the new profile snapshot and recomputed score. */
    suspend fun updateClothingProfile(entryId: Long, profile: Profile, score: PaletteScore)

    /** Deletes an entry and its thumbnail file. */
    suspend fun deleteEntry(entryId: Long)
}

class DefaultHistoryRepository(
    private val dao: HistoryDao,
    private val thumbnailStore: ThumbnailStore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : HistoryRepository {

    override fun observeEntries(): Flow<List<HistoryEntry>> =
        dao.observeEntries().map { list -> list.map { it.toDomain() } }

    override fun observeEntry(entryId: Long): Flow<HistoryEntry?> =
        dao.observeEntry(entryId).map { it?.toDomain() }

    override suspend fun saveClothingResult(
        thumbnailPath: String,
        calibratedArgb: Int,
        profile: Profile?,
        score: PaletteScore
    ): HistoryEntry {
        val now = currentTimeMillis()
        val entry = HistoryEntry(
            id = 0,
            type = HistoryEntryType.CLOTHING,
            name = defaultHistoryEntryName(HistoryEntryType.CLOTHING, now),
            createdAt = now,
            thumbnailPath = thumbnailPath,
            calibratedArgb = calibratedArgb,
            profileId = profile?.id,
            profileName = profile?.name,
            bestColorsArgb = profile?.bestColors.orEmpty().map { it.argb },
            avoidColorsArgb = profile?.avoidColors.orEmpty().map { it.argb },
            score = score
        )
        val id = dao.insert(entry.toEntity())
        return entry.copy(id = id)
    }

    override suspend fun saveObjectResult(
        thumbnailPath: String,
        inputColorsArgb: List<Int>,
        wheel: HarmonyWheel,
        balance: HarmonyBalance,
    ): HistoryEntry {
        require(inputColorsArgb.isNotEmpty()) { "At least one input color is required" }
        val now = currentTimeMillis()
        val entry = HistoryEntry(
            id = 0,
            type = HistoryEntryType.OBJECT,
            name = defaultHistoryEntryName(HistoryEntryType.OBJECT, now),
            createdAt = now,
            thumbnailPath = thumbnailPath,
            calibratedArgb = inputColorsArgb.first(),
            profileId = null,
            profileName = null,
            bestColorsArgb = emptyList(),
            avoidColorsArgb = emptyList(),
            score = PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false),
            inputColorsArgb = inputColorsArgb,
            wheel = wheel,
            balance = balance,
        )
        val id = dao.insert(entry.toEntity())
        return entry.copy(id = id)
    }

    override suspend fun renameEntry(entryId: Long, name: String) {
        dao.updateName(entryId, name)
    }

    override suspend fun updateObjectPick(entryId: Long, argb: Int, sampleX: Double?, sampleY: Double?) {
        dao.updateObjectPick(entryId, argb, listOf(argb), sampleX, sampleY)
    }

    override suspend fun updateClothingPick(entryId: Long, argb: Int, score: PaletteScore, sampleX: Double?, sampleY: Double?) {
        dao.updateClothingPick(
            entryId = entryId,
            argb = argb,
            nearestBestArgb = score.nearestBest?.argb,
            nearestBestDeltaE = score.nearestBest?.deltaE,
            nearestAvoidArgb = score.nearestAvoid?.argb,
            nearestAvoidDeltaE = score.nearestAvoid?.deltaE,
            closerToAvoid = score.closerToAvoid,
            sampleX = sampleX,
            sampleY = sampleY,
        )
    }

    override suspend fun updateClothingProfile(entryId: Long, profile: Profile, score: PaletteScore) {
        dao.updateClothingProfile(
            entryId = entryId,
            profileId = profile.id,
            profileName = profile.name,
            bestColorsArgb = profile.bestColors.map { it.argb },
            avoidColorsArgb = profile.avoidColors.map { it.argb },
            nearestBestArgb = score.nearestBest?.argb,
            nearestBestDeltaE = score.nearestBest?.deltaE,
            nearestAvoidArgb = score.nearestAvoid?.argb,
            nearestAvoidDeltaE = score.nearestAvoid?.deltaE,
            closerToAvoid = score.closerToAvoid,
        )
    }

    override suspend fun deleteEntry(entryId: Long) {
        val entity = dao.getEntry(entryId) ?: return
        dao.delete(entryId)
        thumbnailStore.delete(entity.thumbnailPath)
    }
}
