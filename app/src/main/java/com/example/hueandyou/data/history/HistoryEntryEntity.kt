package com.example.hueandyou.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: HistoryEntryType,
    val name: String,
    val createdAt: Long,
    val thumbnailPath: String,
    val calibratedArgb: Int,
    val profileId: Long?,
    val profileName: String?,
    val bestColorsArgb: List<Int>,
    val avoidColorsArgb: List<Int>,
    val nearestBestArgb: Int?,
    val nearestBestDeltaE: Double?,
    val nearestAvoidArgb: Int?,
    val nearestAvoidDeltaE: Double?,
    val closerToAvoid: Boolean,
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
