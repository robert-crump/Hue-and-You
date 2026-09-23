package com.example.hueandyou.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
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

    @Query("UPDATE history_entries SET name = :name WHERE id = :entryId")
    suspend fun updateName(entryId: Long, name: String)

    @Query("UPDATE history_entries SET wheel = :wheel, balance = :balance WHERE id = :entryId")
    suspend fun updateHarmonyOptions(entryId: Long, wheel: HarmonyWheel, balance: HarmonyBalance)

    @Query("DELETE FROM history_entries WHERE id = :entryId")
    suspend fun delete(entryId: Long)
}
