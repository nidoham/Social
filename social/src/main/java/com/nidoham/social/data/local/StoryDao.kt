package com.nidoham.social.data.local

import androidx.paging.PagingSource
import androidx.room.*
import com.nidoham.social.stories.Story
import com.nidoham.social.stories.StoryType
import com.nidoham.social.stories.StoryVisibility
import kotlinx.coroutines.flow.Flow

/**
 * Optimized DAO using Abstract Class to support concrete methods and Transactions.
 */
@Dao
abstract class StoryDao {

    // ================= INSERT / UPDATE =================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertStory(story: Story): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertStories(stories: List<Story>): List<Long>

    @Update
    abstract suspend fun updateStory(story: Story): Int

    // ================= READ (Single & Flow) =================

    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    abstract suspend fun getStoryById(storyId: String): Story?

    @Query("SELECT * FROM stories WHERE id = :storyId")
    abstract fun getStoryByIdFlow(storyId: String): Flow<Story?>

    // ================= FEED & PAGINATION (Crucial for FB like App) =================

    /**
     * Paging 3 Source for Infinite Scroll (Recommended)
     */
    @Query("""
        SELECT * FROM stories 
        WHERE isDeleted = 0 
        AND isBanned = 0 
        AND expiresAt > :currentTime
        ORDER BY createdAt DESC
    """)
    abstract fun getActiveStoriesPagingSource(
        currentTime: Long = System.currentTimeMillis()
    ): PagingSource<Int, Story>

    /**
     * Manual Pagination
     */
    @Query("""
        SELECT * FROM stories 
        WHERE isDeleted = 0 
        AND isBanned = 0 
        AND expiresAt > :currentTime
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    abstract suspend fun getActiveStoriesPaginated(
        limit: Int,
        offset: Int,
        currentTime: Long = System.currentTimeMillis()
    ): List<Story>

    // ================= FILTERS (Type Safe Enums) =================

    // authorId দিয়ে স্টোরি খোঁজা
    @Query("""
        SELECT * FROM stories 
        WHERE authorId = :authorId 
        AND isDeleted = 0 
        AND isBanned = 0 
        AND expiresAt > :currentTime
        ORDER BY createdAt DESC
    """)
    abstract fun getStoriesByAuthorFlow(
        authorId: String,
        currentTime: Long = System.currentTimeMillis()
    ): Flow<List<Story>>

    // Visibility দিয়ে ফিল্টার (Enum ব্যবহার করা হয়েছে)
    @Query("""
        SELECT * FROM stories 
        WHERE visibility = :visibility
        AND isDeleted = 0 
        AND isBanned = 0 
        AND expiresAt > :currentTime
        ORDER BY createdAt DESC
    """)
    abstract suspend fun getStoriesByVisibility(
        visibility: StoryVisibility, // String এর বদলে Enum
        currentTime: Long = System.currentTimeMillis()
    ): List<Story>

    // ContentType দিয়ে ফিল্টার (Enum ব্যবহার করা হয়েছে)
    @Query("""
        SELECT * FROM stories 
        WHERE contentType = :contentType
        AND isDeleted = 0 
        AND isBanned = 0 
        AND expiresAt > :currentTime
        ORDER BY createdAt DESC
    """)
    abstract suspend fun getStoriesByContentType(
        contentType: StoryType,
        currentTime: Long = System.currentTimeMillis()
    ): List<Story>

    // ================= COUNTERS & UPDATES =================

    @Query("UPDATE stories SET viewsCount = viewsCount + 1 WHERE id = :storyId")
    abstract suspend fun incrementViewCount(storyId: String)

    @Query("UPDATE stories SET reactionsCount = reactionsCount + 1 WHERE id = :storyId")
    abstract suspend fun incrementReactionCount(storyId: String)

    // ================= CLEANUP & STATS =================

    @Query("DELETE FROM stories WHERE id = :storyId")
    abstract suspend fun deleteStoryById(storyId: String)

    @Query("DELETE FROM stories")
    abstract suspend fun deleteAllStories()

    @Query("""
        SELECT 
            COUNT(*) as totalStories,
            SUM(CASE WHEN isDeleted = 0 AND isBanned = 0 AND expiresAt > :currentTime THEN 1 ELSE 0 END) as activeStories,
            SUM(CASE WHEN expiresAt <= :currentTime THEN 1 ELSE 0 END) as expiredStories,
            SUM(CASE WHEN isBanned = 1 THEN 1 ELSE 0 END) as bannedStories,
            SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END) as deletedStories
        FROM stories
    """)
    abstract suspend fun getDatabaseStats(currentTime: Long = System.currentTimeMillis()): DatabaseStats

    // ================= TRANSACTION (Logic inside DAO) =================

    @Query("DELETE FROM stories WHERE expiresAt <= :currentTime")
    protected abstract suspend fun deleteExpiredStoriesInternal(currentTime: Long): Int

    @Query("DELETE FROM stories WHERE isDeleted = 1")
    protected abstract suspend fun deleteMarkedAsDeletedInternal(): Int

    /**
     * Public Transaction Method
     * This runs atomically - both deletes happen or none happen.
     */
    @Transaction
    open suspend fun cleanupDatabase(currentTime: Long = System.currentTimeMillis()): Int {
        val expired = deleteExpiredStoriesInternal(currentTime)
        val deleted = deleteMarkedAsDeletedInternal()
        return expired + deleted
    }
}
