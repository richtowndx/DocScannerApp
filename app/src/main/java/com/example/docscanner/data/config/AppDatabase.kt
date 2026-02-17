package com.example.docscanner.data.config

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.docscanner.data.scanproject.ScanImageDao
import com.example.docscanner.data.scanproject.ScanImageEntity
import com.example.docscanner.data.scanproject.ScanProjectDao
import com.example.docscanner.data.scanproject.ScanProjectEntity

/**
 * 应用数据库
 */
@Database(
    entities = [
        AppConfig::class,
        ScanProjectEntity::class,
        ScanImageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun configDao(): ConfigDao
    abstract fun scanProjectDao(): ScanProjectDao
    abstract fun scanImageDao(): ScanImageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "docscanner_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
