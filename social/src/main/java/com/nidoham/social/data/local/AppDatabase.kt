package com.nidoham.social.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nidoham.social.posts.Post
import com.nidoham.social.stories.Story
import com.nidoham.social.user.User

@Database(
    entities = [
        User::class,
        Story::class,
        Post::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    CommonConverters::class,      // ✅ নতুন যোগ করুন (List<String>)
    UserTypeConverters::class,
    StoryConverters::class,
    PostConverters::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun storyDao(): StoryDao
    abstract fun postDao(): PostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "social_app_db"

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}