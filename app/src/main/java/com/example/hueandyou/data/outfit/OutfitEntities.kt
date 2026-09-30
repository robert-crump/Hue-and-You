package com.example.hueandyou.data.outfit

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.hueandyou.data.history.HistoryEntryEntity

/** An outfit's own row; its items live in [OutfitItemEntity]. No profile or verdict is stored. */
@Entity(tableName = "outfits")
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * One Clothes entry in an outfit, at [position] (0 = first). Deleting either the outfit or the
 * entry cascades to this row, so a deleted Clothes entry drops out of every outfit.
 */
@Entity(
    tableName = "outfit_items",
    primaryKeys = ["outfitId", "entryId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HistoryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("entryId")]
)
data class OutfitItemEntity(
    val outfitId: Long,
    val entryId: Long,
    val position: Int,
)
