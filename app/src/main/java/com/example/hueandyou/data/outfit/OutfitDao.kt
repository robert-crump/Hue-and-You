package com.example.hueandyou.data.outfit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutfitDao {
    @Query("SELECT * FROM outfits ORDER BY createdAt DESC")
    fun observeOutfits(): Flow<List<OutfitEntity>>

    /** Every outfit's items, in position order within each outfit. */
    @Query("SELECT * FROM outfit_items ORDER BY outfitId, position")
    fun observeItems(): Flow<List<OutfitItemEntity>>

    @Insert
    suspend fun insertOutfit(outfit: OutfitEntity): Long

    @Insert
    suspend fun insertItems(items: List<OutfitItemEntity>)

    @Query("UPDATE outfits SET name = :name WHERE id = :outfitId")
    suspend fun updateName(outfitId: Long, name: String)

    @Query("DELETE FROM outfit_items WHERE outfitId = :outfitId")
    suspend fun deleteItems(outfitId: Long)

    /** Deletes the outfit and, via cascade, its items. */
    @Query("DELETE FROM outfits WHERE id = :outfitId")
    suspend fun deleteOutfit(outfitId: Long)

    /** Deletes every outfit (and, via cascade, every item). Used to restore a backup. */
    @Query("DELETE FROM outfits")
    suspend fun deleteAllOutfits()
}
