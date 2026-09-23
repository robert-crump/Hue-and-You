package com.example.hueandyou.data.history

import androidx.room.TypeConverter

class HistoryConverters {
    @TypeConverter
    fun fromHistoryEntryType(type: HistoryEntryType): String = type.name

    @TypeConverter
    fun toHistoryEntryType(value: String): HistoryEntryType = HistoryEntryType.valueOf(value)

    @TypeConverter
    fun fromIntList(values: List<Int>): String = values.joinToString(",")

    @TypeConverter
    fun toIntList(value: String): List<Int> =
        if (value.isEmpty()) emptyList() else value.split(",").map { it.toInt() }
}
