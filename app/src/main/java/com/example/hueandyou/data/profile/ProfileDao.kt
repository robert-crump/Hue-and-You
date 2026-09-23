package com.example.hueandyou.data.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Transaction
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeProfilesWithColors(): Flow<List<ProfileWithColors>>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    fun observeProfileWithColors(profileId: Long): Flow<ProfileWithColors?>

    @Insert
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfile(profileId: Long): ProfileEntity?

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    @Insert
    suspend fun insertColor(color: PaletteColorEntity): Long

    @Query("DELETE FROM palette_colors WHERE id = :colorId")
    suspend fun deleteColor(colorId: Long)

    @Query(
        "SELECT COALESCE(MAX(position), -1) + 1 FROM palette_colors " +
            "WHERE profileId = :profileId AND kind = :kind"
    )
    suspend fun nextPosition(profileId: Long, kind: ColorKind): Int
}
