package com.example.hueandyou.data.profile

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.hueandyou.data.history.HistoryConverters
import com.example.hueandyou.data.history.HistoryDao
import com.example.hueandyou.data.history.HistoryEntryEntity
import com.example.hueandyou.data.outfit.OutfitDao
import com.example.hueandyou.data.outfit.OutfitEntity
import com.example.hueandyou.data.outfit.OutfitItemEntity

@Database(
    entities = [
        ProfileEntity::class,
        PaletteColorEntity::class,
        HistoryEntryEntity::class,
        OutfitEntity::class,
        OutfitItemEntity::class,
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class, HistoryConverters::class)
abstract class HueAndYouDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun historyDao(): HistoryDao
    abstract fun outfitDao(): OutfitDao
}
