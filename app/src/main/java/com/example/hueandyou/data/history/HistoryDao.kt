package com.example.hueandyou.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC")
    fun observeEntries(): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE id = :entryId")
    fun observeEntry(entryId: Long): Flow<HistoryEntryEntity?>

    @Query("SELECT * FROM history_entries WHERE id = :entryId")
    suspend fun getEntry(entryId: Long): HistoryEntryEntity?

    @Insert
    suspend fun insert(entry: HistoryEntryEntity): Long

    /** Bulk insert used when restoring a backup. */
    @Insert
    suspend fun insertAll(entries: List<HistoryEntryEntity>)

    /** Deletes every entry. Used to restore a backup. */
    @Query("DELETE FROM history_entries")
    suspend fun deleteAllEntries()

    @Query("UPDATE history_entries SET name = :name WHERE id = :entryId")
    suspend fun updateName(entryId: Long, name: String)

    /** A re-pick on an OBJECT entry: the new color and where it was sampled from (null = center box). */
    @Query(
        "UPDATE history_entries SET calibratedArgb = :argb, inputColorsArgb = :inputColorsArgb, " +
            "sampleX = :sampleX, sampleY = :sampleY WHERE id = :entryId"
    )
    suspend fun updateObjectPick(
        entryId: Long,
        argb: Int,
        inputColorsArgb: List<Int>,
        sampleX: Double?,
        sampleY: Double?,
    )

    /** A re-pick on a CLOTHING entry: the new color, its recomputed score, and where it was sampled from. */
    @Query(
        "UPDATE history_entries SET calibratedArgb = :argb, " +
            "nearestBestArgb = :nearestBestArgb, nearestBestDeltaE = :nearestBestDeltaE, " +
            "nearestAvoidArgb = :nearestAvoidArgb, nearestAvoidDeltaE = :nearestAvoidDeltaE, " +
            "closerToAvoid = :closerToAvoid, sampleX = :sampleX, sampleY = :sampleY WHERE id = :entryId"
    )
    suspend fun updateClothingPick(
        entryId: Long,
        argb: Int,
        nearestBestArgb: Int?,
        nearestBestDeltaE: Double?,
        nearestAvoidArgb: Int?,
        nearestAvoidDeltaE: Double?,
        closerToAvoid: Boolean,
        sampleX: Double?,
        sampleY: Double?,
    )

    @Query("DELETE FROM history_entries WHERE id = :entryId")
    suspend fun delete(entryId: Long)
}
