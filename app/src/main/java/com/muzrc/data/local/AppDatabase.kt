package com.muzrc.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppProfileEntity::class,
        QuietScheduleEntity::class,
        ActivityLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appProfileDao(): AppProfileDao
    abstract fun quietScheduleDao(): QuietScheduleDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "senyap_pintar_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
