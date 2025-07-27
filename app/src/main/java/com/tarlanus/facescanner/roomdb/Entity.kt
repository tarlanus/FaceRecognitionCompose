package com.tarlanus.facerecognizerv01.roomdb

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "savedFaces")
data class SavedFaces(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "uniqueID") val uniqueID : Int,
    @ColumnInfo(name = "name") val name : String,
    @ColumnInfo(name = "embedding") val embedding : String
    )