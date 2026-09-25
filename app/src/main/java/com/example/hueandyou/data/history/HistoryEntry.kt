package com.example.hueandyou.data.history

import com.example.hueandyou.colorspace.ColorMatch
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.PaletteScore

data class HistoryEntry(
    val id: Long,
    val type: HistoryEntryType,
    val name: String,
    val createdAt: Long,
    val thumbnailPath: String,
    val calibratedArgb: Int,
    val profileId: Long?,
    val profileName: String?,
    val bestColorsArgb: List<Int>,
    val avoidColorsArgb: List<Int>,
    val score: PaletteScore,
    /** Object-only: the colors selected from the photo. Empty for [HistoryEntryType.CLOTHING]. */
    val inputColorsArgb: List<Int> = emptyList(),
    /** Object-only: null for [HistoryEntryType.CLOTHING]. */
    val wheel: HarmonyWheel? = null,
    /** Object-only: null for [HistoryEntryType.CLOTHING]. */
    val balance: HarmonyBalance? = null,
    /** Where [calibratedArgb] was sampled from, normalized to the photo's size; null = center box. */
    val sampleX: Double? = null,
    val sampleY: Double? = null,
)

internal fun HistoryEntryEntity.toDomain(): HistoryEntry {
    val nearestBest = nearestBestArgb?.let { argb -> ColorMatch(argb, requireNotNull(nearestBestDeltaE)) }
    val nearestAvoid = nearestAvoidArgb?.let { argb -> ColorMatch(argb, requireNotNull(nearestAvoidDeltaE)) }
    return HistoryEntry(
        id = id,
        type = type,
        name = name,
        createdAt = createdAt,
        thumbnailPath = thumbnailPath,
        calibratedArgb = calibratedArgb,
        profileId = profileId,
        profileName = profileName,
        bestColorsArgb = bestColorsArgb,
        avoidColorsArgb = avoidColorsArgb,
        score = PaletteScore(nearestBest, nearestAvoid, closerToAvoid),
        inputColorsArgb = inputColorsArgb,
        wheel = wheel,
        balance = balance,
        sampleX = sampleX,
        sampleY = sampleY,
    )
}

internal fun HistoryEntry.toEntity(): HistoryEntryEntity = HistoryEntryEntity(
    id = id,
    type = type,
    name = name,
    createdAt = createdAt,
    thumbnailPath = thumbnailPath,
    calibratedArgb = calibratedArgb,
    profileId = profileId,
    profileName = profileName,
    bestColorsArgb = bestColorsArgb,
    avoidColorsArgb = avoidColorsArgb,
    nearestBestArgb = score.nearestBest?.argb,
    nearestBestDeltaE = score.nearestBest?.deltaE,
    nearestAvoidArgb = score.nearestAvoid?.argb,
    nearestAvoidDeltaE = score.nearestAvoid?.deltaE,
    closerToAvoid = score.closerToAvoid,
    inputColorsArgb = inputColorsArgb,
    wheel = wheel,
    balance = balance,
    sampleX = sampleX,
    sampleY = sampleY,
)

/** Default name a newly-saved entry gets, before the user edits it: just its type. */
internal fun defaultHistoryEntryName(type: HistoryEntryType): String =
    when (type) {
        HistoryEntryType.CLOTHING -> "Clothing"
        HistoryEntryType.OBJECT -> "Object"
    }
