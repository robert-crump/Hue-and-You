package com.example.hueandyou.data.history

import androidx.room.TypeConverter
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel

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

    @TypeConverter
    fun fromHarmonyWheel(wheel: HarmonyWheel?): String? = wheel?.name

    @TypeConverter
    fun toHarmonyWheel(value: String?): HarmonyWheel? = value?.let { HarmonyWheel.valueOf(it) }

    @TypeConverter
    fun fromHarmonyBalance(balance: HarmonyBalance?): String? = balance?.name

    @TypeConverter
    fun toHarmonyBalance(value: String?): HarmonyBalance? = value?.let { HarmonyBalance.valueOf(it) }
}
