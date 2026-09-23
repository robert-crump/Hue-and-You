package com.example.hueandyou.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val closerToAvoid: Boolean
)
