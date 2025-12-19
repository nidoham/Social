package com.nidoham.social.stories

import androidx.room.*

/**
 * Data Access Object for Story entities in Room Database
 * Handles all database operations for story caching
 */
@Dao
interface StoryDao {

    /**
     * Insert or update a story in the cache
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: Story)

    /**
     * Insert or update multiple stories in the cache
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<Story>)

    /**
     * Get a story by ID from cache
     */
    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    suspend fun getStoryById(storyId: String): Story?

    /**
     * Get all active stories (not deleted, not banned, not expired)
     * within the last 24 hours
     */
    @Query("""
        SELECT * FROM stories 
        WHERE isDeleted = 0 
        AND isBanned = 0 
        AND expiresAt > :currentTime
        ORDER BY createdAt DESC
    """)
    suspend fun getActiveStories(currentTime: Long): List<Story>

    /**
     * Get paginated active stories
     */
    @Query("""
        SELECT * FROM stories 
        WHERE isDeleted = 0 
        AND isBanned = 0 
        AND expiresAt > :currentTime
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getActiveStoriesPaginated(
        currentTime: Long,
        limit: Int,
        offset: Int
    ): List<Story>

    /**
     * Get stories by author ID
     */
    @Query("""
        SELECT * FROM stories 
        WHERE authorId = :authorId 
        AND isDeleted = 0 
        AND isBanned = 0 
        AND expiresAt > :currentTime
        ORDER BY createdAt DESC
    """)
    suspend fun getStoriesByAuthor(authorId: String, currentTime: Long): List<Story>

    /**
     * Get total count of active cached stories
     */
    @Query("""
        SELECT COUNT(*) FROM stories 
        WHERE isDeleted = 0 
        AND isBanned = 0 
        AND expiresAt > :currentTime
    """)
    suspend fun getActiveStoryCount(currentTime: Long): Int

    /**
     * Delete a story by ID from cache
     */
    @Query("DELETE FROM stories WHERE id = :storyId")
    suspend fun deleteStoryById(storyId: String)

    /**
     * Delete expired stories
     */
    @Query("DELETE FROM stories WHERE expiresAt <= :currentTime")
    suspend fun deleteExpiredStories(currentTime: Long)

    /**
     * Delete all stories from cache
     */
    @Query("DELETE FROM stories")
    suspend fun deleteAllStories()

    /**
     * Update story view count
     */
    @Query("UPDATE stories SET viewsCount = viewsCount + 1 WHERE id = :storyId")
    suspend fun incrementViewCount(storyId: String)
}