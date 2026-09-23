package com.example.hueandyou.data.profile

import androidx.room.Embedded
import androidx.room.Relation

data class ProfileWithColors(
    @Embedded val profile: ProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profileId")
    val colors: List<PaletteColorEntity>
)
