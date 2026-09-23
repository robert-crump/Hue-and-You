package com.example.hueandyou.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bump when the JSON shape changes in a way older parsers can't read; see [JsonBackupSerializer]. */
internal const val BACKUP_SCHEMA_VERSION = 1

@Serializable
internal data class BackupDocumentDto(
    val schemaVersion: Int,
    val exportedAt: String,
    val settings: BackupSettingsDto,
    val profiles: List<BackupProfileDto>,
    val history: List<BackupHistoryDto>,
)

@Serializable
internal data class BackupSettingsDto(
    val defaultWheel: String,
    val defaultBalance: String,
)

@Serializable
internal data class BackupProfileDto(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val best: List<String> = emptyList(),
    val avoid: List<String> = emptyList(),
)

@Serializable
internal data class BackupHistoryDto(
    val id: Long,
    val type: String,
    val name: String,
    val createdAt: Long,
    val thumbnailBase64: String,
    /** Object-only: the colors selected from the photo. Empty for a clothing entry. */
    val inputColors: List<String> = emptyList(),
    /** Present only for a clothing entry. */
    val clothing: BackupClothingDto? = null,
    /** Present only for an object entry. `object` is reserved in Kotlin, hence the [SerialName]. */
    @SerialName("object") val objectResult: BackupObjectDto? = null,
)

@Serializable
internal data class BackupClothingDto(
    val calibratedArgb: String,
    val profileId: Long? = null,
    val profileName: String? = null,
    val bestColors: List<String> = emptyList(),
    val avoidColors: List<String> = emptyList(),
    val nearestBest: BackupColorMatchDto? = null,
    val nearestAvoid: BackupColorMatchDto? = null,
    val closerToAvoid: Boolean = false,
)

@Serializable
internal data class BackupColorMatchDto(
    val argb: String,
    val deltaE: Double,
    val band: String,
)

@Serializable
internal data class BackupObjectDto(
    val wheel: String,
    val balance: String,
)
