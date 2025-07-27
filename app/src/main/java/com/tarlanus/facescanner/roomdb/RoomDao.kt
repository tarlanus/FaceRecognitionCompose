package com.tarlanus.facerecognizerv01.roomdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query

@Dao
interface RoomDao {

    @Insert(onConflict = REPLACE)
    suspend fun insertData(data : SavedFaces)

    @Query("SELECT * FROM savedFaces")
    suspend fun getSaves() : List<SavedFaces>
}