package com.nidoham.social.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database for caching user data
 * Version 1: Initial database schema
 */
@Database(
    entities = [User::class],
    version = 1,
    exportSchema = false
)
abstract class UserDatabase : RoomDatabase() {

    /**
     * Provides access to UserDao
     */
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        private const val DATABASE_NAME = "user_cache_database"

        /**
         * Get singleton instance of UserDatabase
         */
        fun getInstance(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
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