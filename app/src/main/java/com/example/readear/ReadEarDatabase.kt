package com.example.readear

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database
 */
@Database(
    entities = [Book::class, Page::class, ReadingProgress::class],
    version = 1,
    exportSchema = false
)
abstract class ReadEarDatabase : RoomDatabase() {
    
    abstract fun bookDao(): BookDao
    
    companion object {
        @Volatile
        private var INSTANCE: ReadEarDatabase? = null
        
        fun getDatabase(context: Context): ReadEarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReadEarDatabase::class.java,
                    "readea_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
