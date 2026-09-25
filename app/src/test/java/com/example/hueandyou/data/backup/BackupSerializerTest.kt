package com.example.hueandyou.data.backup

import com.example.hueandyou.colorspace.ColorMatch
import com.example.hueandyou.colorspace.ColorMatchBand
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.history.HistoryEntryType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {

    private val serializer: BackupSerializer = JsonBackupSerializer()
    private val rawJson = Json { prettyPrint = true }

    private fun sampleData(): BackupData = BackupData(
        defaultWheel = HarmonyWheel.SCREEN,
        defaultBalance = HarmonyBalance.SOFTENED,
        profiles = listOf(
            BackupProfile(
                id = 1L,
                name = "Autumn",
                createdAt = 1_000L,
                updatedAt = 2_000L,
                bestColorsArgb = listOf(0xFF112233.toInt(), 0xFF445566.toInt()),
                avoidColorsArgb = listOf(0xFF778899.toInt()),
            )
        ),
        history = listOf(
            BackupHistoryEntry(
                id = 10L,
                type = HistoryEntryType.CLOTHING,
                name = "Clothing Sep 1",
                createdAt = 3_000L,
                thumbnailBytes = byteArrayOf(1, 2, 3, 4),
                calibratedArgb = 0xFF223344.toInt(),
                profileId = 1L,
                profileName = "Autumn",
                bestColorsArgb = listOf(0xFF112233.toInt()),
                avoidColorsArgb = listOf(0xFF778899.toInt()),
                score = PaletteScore(
                    nearestBest = ColorMatch(0xFF112233.toInt(), 2.0, ColorMatchBand.MATCH),
                    nearestAvoid = ColorMatch(0xFF778899.toInt(), 20.0, ColorMatchBand.FAR),
                    closerToAvoid = false,
                ),
                inputColorsArgb = emptyList(),
                wheel = null,
                balance = null,
            ),
            BackupHistoryEntry(
                id = 11L,
                type = HistoryEntryType.OBJECT,
                name = "Object Sep 2",
                createdAt = 4_000L,
                thumbnailBytes = byteArrayOf(5, 6, 7),
                calibratedArgb = 0xFF001122.toInt(),
                profileId = null,
                profileName = null,
                bestColorsArgb = emptyList(),
                avoidColorsArgb = emptyList(),
                score = PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false),
                inputColorsArgb = listOf(0xFF001122.toInt(), 0xFF334455.toInt()),
                wheel = HarmonyWheel.TRADITIONAL,
                balance = HarmonyBalance.FAITHFUL,
                sampleX = 0.25,
                sampleY = 0.75,
            ),
        ),
    )

    /** Parses [json], applies [mutate] to the top-level object, and re-serializes it. */
    private fun mutated(json: String, mutate: (MutableMap<String, JsonElement>) -> Unit): String {
        val obj = rawJson.parseToJsonElement(json).jsonObject.toMutableMap()
        mutate(obj)
        return rawJson.encodeToString(JsonObject.serializer(), JsonObject(obj))
    }

    @Test
    fun serializeThenDeserialize_roundTripsExactly() {
        val original = sampleData()

        val restored = serializer.deserialize(serializer.serialize(original))

        assertEquals(original, restored)
    }

    @Test
    fun deserialize_ignoresUnknownFields() {
        val json = serializer.serialize(sampleData())
        val withExtraField = mutated(json) { it["futureField"] = JsonPrimitive("ignored") }

        val restored = serializer.deserialize(withExtraField)

        assertEquals(sampleData(), restored)
    }

    @Test
    fun deserialize_withInvalidJson_throwsAndDoesNotThrowSomethingElse() {
        assertThrows(BackupImportException::class.java) {
            serializer.deserialize("{ this is not json")
        }
    }

    @Test
    fun deserialize_withMissingRequiredField_throws() {
        val json = serializer.serialize(sampleData())
        val withoutSettings = mutated(json) { it.remove("settings") }

        assertThrows(BackupImportException::class.java) {
            serializer.deserialize(withoutSettings)
        }
    }

    @Test
    fun deserialize_withoutSampleFields_defaultsToNullForOldBackups() {
        val json = serializer.serialize(sampleData())
        val withoutSampleFields = mutated(json) { obj ->
            val history = obj.getValue("history").jsonArray.map { entry ->
                JsonObject(entry.jsonObject.filterKeys { it != "sampleX" && it != "sampleY" })
            }
            obj["history"] = JsonArray(history)
        }

        val restored = serializer.deserialize(withoutSampleFields)

        restored.history.forEach { entry ->
            assertNull(entry.sampleX)
            assertNull(entry.sampleY)
        }
    }

    @Test
    fun deserialize_withNewerSchemaVersion_isRejectedWithAClearMessage() {
        val json = serializer.serialize(sampleData())
        val fromTheFuture = mutated(json) { it["schemaVersion"] = JsonPrimitive(BACKUP_SCHEMA_VERSION + 1) }

        val exception = assertThrows(BackupImportException::class.java) {
            serializer.deserialize(fromTheFuture)
        }

        assertTrue(exception.message.orEmpty().contains("newer"))
    }
}
