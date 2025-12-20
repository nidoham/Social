package com.nidoham.social.posts

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database for Posts
 */
@Database(entities = [Post::class], version = 2, exportSchema = true)
abstract class PostDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao

    companion object {
        @Volatile
        private var INSTANCE: PostDatabase? = null

        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add any schema changes here
                // Example: If you added new columns in version 2
                // database.execSQL("ALTER TABLE posts ADD COLUMN newColumn TEXT DEFAULT ''")
            }
        }

        fun getInstance(context: Context): PostDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PostDatabase::class.java,
                    "post_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // Use with caution in production
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Initialize database if needed
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Clear the database instance (useful for testing)
         */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}