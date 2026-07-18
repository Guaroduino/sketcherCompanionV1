package com.sketcher.sketchercompanionv1.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sketcher.sketchercompanionv1.data.db.entities.UserToolEntity
import com.sketcher.sketchercompanionv1.data.db.entities.WorkspaceProfileEntity

@Database(
    entities = [UserToolEntity::class, WorkspaceProfileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
    abstract fun workspaceProfileDao(): WorkspaceProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sketcher_companion_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
