package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaItemEntity::class,
        FileItemEntity::class,
        OcrTextEntity::class,
        VisualFeatureEntity::class,
        LabelEntity::class,
        ReferenceImageEntity::class,
        LabelMediaAssociationEntity::class,
        ConfirmedMatchEntity::class,
        DuplicateGroupEntity::class,
        IndexStatusEntity::class,
        UserSettingsEntity::class,
        SearchHistoryEntity::class,
        IndexedItemEntity::class,
        OcrCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FindAnythingDatabase : RoomDatabase() {
    abstract fun dao(): FindAnythingDao

    companion object {
        @Volatile
        private var INSTANCE: FindAnythingDatabase? = null

        fun getInstance(context: Context): FindAnythingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FindAnythingDatabase::class.java,
                    "findanything_local.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

