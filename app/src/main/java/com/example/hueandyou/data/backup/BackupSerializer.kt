package com.example.hueandyou.data.backup

import com.example.hueandyou.colorspace.ColorMatch
import com.example.hueandyou.colorspace.ColorMatchBand
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.colorspace.formatHexColor
import com.example.hueandyou.colorspace.parseHexColor
import com.example.hueandyou.data.history.HistoryEntryType
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Thrown when a backup file can't be imported; [message] is written to be shown to the user directly. */
class BackupImportException(message: String) : Exception(message)

interface BackupSerializer {
    /** Converts [data] to a versioned JSON backup document. */
    fun serialize(data: BackupData, exportedAt: Instant = Instant.now()): String

    /**
     * Parses a JSON backup document back into [BackupData]. Pure: performs no I/O and has no
     * side effects, so all validation happens before a caller writes anything.
     *
     * @throws BackupImportException if the file is malformed, missing a required field, or was
     * written by a newer, unrecognized schema version.
     */
    fun deserialize(json: String): BackupData
}

class JsonBackupSerializer : BackupSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun serialize(data: BackupData, exportedAt: Instant): String {
        val document = BackupDocumentDto(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = exportedAt.toString(),
            settings = BackupSettingsDto(
                defaultWheel = data.defaultWheel.name,
                defaultBalance = data.defaultBalance.name,
            ),
            profiles = data.profiles.map { it.toDto() },
            history = data.history.map { it.toDto() },
        )
        return json.encodeToString(BackupDocumentDto.serializer(), document)
    }

    override fun deserialize(json: String): BackupData {
        val schemaVersion = peekSchemaVersion(json)
        if (schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw BackupImportException(
                "This backup was made with a newer version of Hue and You (schema $schemaVersion). " +
                    "Update the app before importing it."
            )
        }
        val document = try {
            this.json.decodeFromString(BackupDocumentDto.serializer(), json)
        } catch (e: SerializationException) {
            throw BackupImportException("This file isn't a valid Hue and You backup.")
        }
        return document.toDomain()
    }

    private fun peekSchemaVersion(text: String): Int {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw BackupImportException("This file isn't valid JSON.")
        }
        val obj = element as? JsonObject
            ?: throw BackupImportException("This file isn't a valid Hue and You backup.")
        val versionElement = obj["schemaVersion"] as? JsonPrimitive
            ?: throw BackupImportException("This file isn't a valid Hue and You backup.")
        return versionElement.content.toIntOrNull()
            ?: throw BackupImportException("This file isn't a valid Hue and You backup.")
    }
}

private fun BackupProfile.toDto(): BackupProfileDto = BackupProfileDto(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    best = bestColorsArgb.map(::formatHexColor),
    avoid = avoidColorsArgb.map(::formatHexColor),
)

private fun BackupHistoryEntry.toDto(): BackupHistoryDto = BackupHistoryDto(
    id = id,
    type = type.name,
    name = name,
    createdAt = createdAt,
    thumbnailBase64 = Base64.getEncoder().encodeToString(thumbnailBytes),
    inputColors = inputColorsArgb.map(::formatHexColor),
    clothing = if (type == HistoryEntryType.CLOTHING) {
        BackupClothingDto(
            calibratedArgb = formatHexColor(calibratedArgb),
            profileId = profileId,
            profileName = profileName,
            bestColors = bestColorsArgb.map(::formatHexColor),
            avoidColors = avoidColorsArgb.map(::formatHexColor),
            nearestBest = score.nearestBest?.toDto(),
            nearestAvoid = score.nearestAvoid?.toDto(),
            closerToAvoid = score.closerToAvoid,
        )
    } else {
        null
    },
    objectResult = if (type == HistoryEntryType.OBJECT) {
        BackupObjectDto(wheel = requireNotNull(wheel).name, balance = requireNotNull(balance).name)
    } else {
        null
    },
)

private fun ColorMatch.toDto(): BackupColorMatchDto =
    BackupColorMatchDto(argb = formatHexColor(argb), deltaE = deltaE, band = band.name)

internal fun BackupDocumentDto.toDomain(): BackupData = BackupData(
    defaultWheel = settings.defaultWheel.toEnumOrThrow(),
    defaultBalance = settings.defaultBalance.toEnumOrThrow(),
    profiles = profiles.map { it.toDomain() },
    history = history.map { it.toDomain() },
)

private fun BackupProfileDto.toDomain(): BackupProfile = BackupProfile(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    bestColorsArgb = best.map { it.toArgbOrThrow() },
    avoidColorsArgb = avoid.map { it.toArgbOrThrow() },
)

private fun BackupHistoryDto.toDomain(): BackupHistoryEntry {
    val entryType = type.toEnumOrThrow<HistoryEntryType>()
    val thumbnailBytes = try {
        Base64.getDecoder().decode(thumbnailBase64)
    } catch (e: IllegalArgumentException) {
        throw BackupImportException("A history entry's thumbnail is corrupted.")
    }
    return when (entryType) {
        HistoryEntryType.CLOTHING -> {
            val clothing = clothing
                ?: throw BackupImportException("A clothing history entry is missing its result.")
            BackupHistoryEntry(
                id = id,
                type = entryType,
                name = name,
                createdAt = createdAt,
                thumbnailBytes = thumbnailBytes,
                calibratedArgb = clothing.calibratedArgb.toArgbOrThrow(),
                profileId = clothing.profileId,
                profileName = clothing.profileName,
                bestColorsArgb = clothing.bestColors.map { it.toArgbOrThrow() },
                avoidColorsArgb = clothing.avoidColors.map { it.toArgbOrThrow() },
                score = PaletteScore(
                    nearestBest = clothing.nearestBest?.toDomain(),
                    nearestAvoid = clothing.nearestAvoid?.toDomain(),
                    closerToAvoid = clothing.closerToAvoid,
                ),
                inputColorsArgb = emptyList(),
                wheel = null,
                balance = null,
            )
        }
        HistoryEntryType.OBJECT -> {
            val objectResult = objectResult
                ?: throw BackupImportException("An object history entry is missing its result.")
            val colors = inputColors.map { it.toArgbOrThrow() }
            if (colors.isEmpty()) {
                throw BackupImportException("An object history entry has no input colors.")
            }
            BackupHistoryEntry(
                id = id,
                type = entryType,
                name = name,
                createdAt = createdAt,
                thumbnailBytes = thumbnailBytes,
                calibratedArgb = colors.first(),
                profileId = null,
                profileName = null,
                bestColorsArgb = emptyList(),
                avoidColorsArgb = emptyList(),
                score = PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false),
                inputColorsArgb = colors,
                wheel = objectResult.wheel.toEnumOrThrow(),
                balance = objectResult.balance.toEnumOrThrow(),
            )
        }
    }
}

private fun BackupColorMatchDto.toDomain(): ColorMatch =
    ColorMatch(argb = argb.toArgbOrThrow(), deltaE = deltaE, band = band.toEnumOrThrow())

private fun String.toArgbOrThrow(): Int =
    parseHexColor(this) ?: throw BackupImportException("This file contains an invalid color: \"$this\".")

private inline fun <reified T : Enum<T>> String.toEnumOrThrow(): T =
    enumValues<T>().firstOrNull { it.name == this }
        ?: throw BackupImportException("This file contains an unrecognized value: \"$this\".")
