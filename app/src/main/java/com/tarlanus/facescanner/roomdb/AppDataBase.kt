package com.tarlanus.facerecognizerv01.roomdb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedFaces::class], exportSchema = false, version = 1)
abstract class AppDataBase : RoomDatabase() {

    abstract fun getRoomDao() : RoomDao
    companion object {
        @Volatile

        var ROOM_INSTANCE : AppDataBase? = null
        fun getRoomInstance(context : Context) : AppDataBase {
            return ROOM_INSTANCE ?: synchronized(this) {
                val roomDb = Room.databaseBuilder(context, AppDataBase::class.java, "facesdb.sqlite")
                    .setJournalMode(JournalMode.TRUNCATE)
                    .build()
                ROOM_INSTANCE = roomDb
                roomDb

            }
        }

    }

}