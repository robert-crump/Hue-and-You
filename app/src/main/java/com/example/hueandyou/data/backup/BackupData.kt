package com.example.hueandyou.data.backup

import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.history.HistoryEntryType

/** The complete dataset a backup covers: settings defaults, profiles with their colors, and history. */
data class BackupData(
    val defaultWheel: HarmonyWheel,
    val defaultBalance: HarmonyBalance,
    val profiles: List<BackupProfile>,
    val history: List<BackupHistoryEntry>,
)

data class BackupProfile(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val bestColorsArgb: List<Int>,
    val avoidColorsArgb: List<Int>,
)

/** [profileId], when present, refers to a [BackupProfile.id] in the same [BackupData.profiles] list. */
data class BackupHistoryEntry(
    val id: Long,
    val type: HistoryEntryType,
    val name: String,
    val createdAt: Long,
    val thumbnailBytes: ByteArray,
    val calibratedArgb: Int,
    val profileId: Long?,
    val profileName: String?,
    val bestColorsArgb: List<Int>,
    val avoidColorsArgb: List<Int>,
    val score: PaletteScore,
    val inputColorsArgb: List<Int>,
    val wheel: HarmonyWheel?,
    val balance: HarmonyBalance?,
    /** Where [calibratedArgb] was sampled from, normalized to the photo's size; null = center box. */
    val sampleX: Double? = null,
    val sampleY: Double? = null,
) {
    // ByteArray breaks data-class equals/hashCode (identity, not content) - compare content instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupHistoryEntry) return false
        return id == other.id &&
            type == other.type &&
            name == other.name &&
            createdAt == other.createdAt &&
            thumbnailBytes.contentEquals(other.thumbnailBytes) &&
            calibratedArgb == other.calibratedArgb &&
            profileId == other.profileId &&
            profileName == other.profileName &&
            bestColorsArgb == other.bestColorsArgb &&
            avoidColorsArgb == other.avoidColorsArgb &&
            score == other.score &&
            inputColorsArgb == other.inputColorsArgb &&
            wheel == other.wheel &&
            balance == other.balance &&
            sampleX == other.sampleX &&
            sampleY == other.sampleY
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + thumbnailBytes.contentHashCode()
        result = 31 * result + calibratedArgb
        result = 31 * result + (profileId?.hashCode() ?: 0)
        result = 31 * result + (profileName?.hashCode() ?: 0)
        result = 31 * result + bestColorsArgb.hashCode()
        result = 31 * result + avoidColorsArgb.hashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + inputColorsArgb.hashCode()
        result = 31 * result + (wheel?.hashCode() ?: 0)
        result = 31 * result + (balance?.hashCode() ?: 0)
        result = 31 * result + (sampleX?.hashCode() ?: 0)
        result = 31 * result + (sampleY?.hashCode() ?: 0)
        return result
    }
}
