package com.example.hueandyou.data.history

import com.example.hueandyou.colorspace.ColorMatch
import com.example.hueandyou.colorspace.ColorMatchBand
import com.example.hueandyou.colorspace.PaletteScore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val score: PaletteScore
)

internal fun HistoryEntryEntity.toDomain(): HistoryEntry {
    val nearestBest = nearestBestArgb?.let { argb ->
        val deltaE = requireNotNull(nearestBestDeltaE)
        ColorMatch(argb, deltaE, ColorMatchBand.forDeltaE(deltaE))
    }
    val nearestAvoid = nearestAvoidArgb?.let { argb ->
        val deltaE = requireNotNull(nearestAvoidDeltaE)
        ColorMatch(argb, deltaE, ColorMatchBand.forDeltaE(deltaE))
    }
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
        score = PaletteScore(nearestBest, nearestAvoid, closerToAvoid)
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
    closerToAvoid = score.closerToAvoid
)

private val defaultNameDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault())

/** Default "type + date/time" name a newly-saved entry gets, before the user edits it. */
internal fun defaultHistoryEntryName(type: HistoryEntryType, timestampMillis: Long): String {
    val label = when (type) {
        HistoryEntryType.CLOTHING -> "Clothing"
        HistoryEntryType.OBJECT -> "Object"
    }
    val formatted = Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(defaultNameDateFormatter)
    return "$label $formatted"
}
