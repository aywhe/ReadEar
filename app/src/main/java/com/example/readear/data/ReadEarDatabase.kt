package com.example.readear.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database
 */
@Database(
    entities = [Book::class, Page::class, ReadingProgress::class],
    version = 2,
    exportSchema = true
)
abstract class ReadEarDatabase : RoomDatabase() {
    
    abstract fun bookDao(): BookDao
    
    companion object {
        @Volatile
        private var INSTANCE: ReadEarDatabase? = null
        
        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 如果需要迁移逻辑，在这里添加
                // 目前版本 2 只是启用了 schema 导出，不需要实际的数据迁移
            }
        }
        
        fun getDatabase(context: Context): ReadEarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReadEarDatabase::class.java,
                    "readea_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
