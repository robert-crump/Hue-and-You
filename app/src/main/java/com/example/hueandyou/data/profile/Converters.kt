package com.example.hueandyou.data.profile

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromColorKind(kind: ColorKind): String = kind.name

    @TypeConverter
    fun toColorKind(value: String): ColorKind = ColorKind.valueOf(value)
}
