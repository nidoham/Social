package com.nidoham.social.stories

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room Database for caching story data
 * Version 1: Initial database schema
 */
@Database(
    entities = [Story::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(StoryConverters::class)
abstract class StoryDatabase : RoomDatabase() {

    /**
     * Provides access to StoryDao
     */
    abstract fun storyDao(): StoryDao

    companion object {
        @Volatile
        private var INSTANCE: StoryDatabase? = null

        private const val DATABASE_NAME = "story_cache_database"

        /**
         * Get singleton instance of StoryDatabase
         */
        fun getInstance(context: Context): StoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StoryDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Destroy database instance (useful for testing)
         */
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}