package com.nidoham.social.stories

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.nidoham.social.user.User
import com.nidoham.social.user.UserExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * StoryExtractor handles story data operations with Firestore and Room caching
 * Implements cache-first strategy and fetches stories with their authors
 * Only returns stories from the last 24 hours
 */
class StoryExtractor(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val storiesCollection = firestore.collection("stories")
    private val database = StoryDatabase.getInstance(context)
    private val storyDao = database.storyDao()
    private val userExtractor = UserExtractor(context)

    // Store last document for pagination
    private var lastDocument: DocumentSnapshot? = null

    companion object {
        private const val PAGE_SIZE = 20
        private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Push a story to Firestore and update cache
     * @param story Story to push
     * @return Result indicating success or failure
     */
    suspend fun pushStory(story: Story): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Push to Firestore
            storiesCollection.document(story.id)
                .set(story.toMap())
                .await()

            // Update cache
            storyDao.insertStory(story)

            // Clean up expired stories
            cleanupExpiredStories()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a story from Firestore and cache
     * @param storyId Story ID to remove
     * @return Result indicating success or failure
     */
    suspend fun removeStory(storyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Remove from Firestore
            storiesCollection.document(storyId)
                .delete()
                .await()

            // Remove from cache
            storyDao.deleteStoryById(storyId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch paginated stories with their authors (20 per page)
     * Only returns stories from the last 24 hours
     * Checks cache first, then Firestore if not sufficient
     * @param page Page number (0-indexed)
     * @return Result containing list of StoryWithAuthor
     */
    suspend fun fetchStoriesPage(page: Int): Result<List<StoryWithAuthor>> = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val twentyFourHoursAgo = currentTime - TWENTY_FOUR_HOURS_MS

            // Reset pagination if requesting first page
            if (page == 0) {
                lastDocument = null
            }

            // Build Firestore query
            var query: Query = storiesCollection
                .whereEqualTo("isDeleted", false)
                .whereEqualTo("isBanned", false)
                .whereGreaterThan("createdAt", twentyFourHoursAgo)
                .orderBy("createdAt", Query.Direction.DESCENDING)

            // Add pagination cursor if available
            lastDocument?.let {
                query = query.startAfter(it)
            }

            // Execute query
            val querySnapshot = query
                .limit(PAGE_SIZE.toLong())
                .get()
                .await()

            // Update last document for next page
            if (querySnapshot.documents.isNotEmpty()) {
                lastDocument = querySnapshot.documents.last()
            }

            // Parse stories and filter by expiration
            val stories = querySnapshot.documents.mapNotNull { doc ->
                try {
                    val story = Story.fromMap(doc.data ?: emptyMap())
                    // Double-check expiration and 24-hour window
                    if (story.expiresAt > currentTime && story.createdAt > twentyFourHoursAgo) {
                        story
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            // Update cache
            if (stories.isNotEmpty()) {
                storyDao.insertStories(stories)
                cleanupExpiredStories()
            }

            // Fetch authors for stories
            val storiesWithAuthors = fetchAuthorsForStories(stories)

            Result.success(storiesWithAuthors)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch stories by a specific author with author info
     * @param authorId Author's user ID
     * @return Result containing list of StoryWithAuthor
     */
    suspend fun fetchStoriesByAuthor(authorId: String): Result<List<StoryWithAuthor>> =
        withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                val twentyFourHoursAgo = currentTime - TWENTY_FOUR_HOURS_MS

                // Try cache first
                var stories = storyDao.getStoriesByAuthor(authorId, currentTime)

                // If not in cache, fetch from Firestore
                if (stories.isEmpty()) {
                    val querySnapshot = storiesCollection
                        .whereEqualTo("authorId", authorId)
                        .whereEqualTo("isDeleted", false)
                        .whereEqualTo("isBanned", false)
                        .whereGreaterThan("createdAt", twentyFourHoursAgo)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .get()
                        .await()

                    // Parse and filter by expiration
                    stories = querySnapshot.documents.mapNotNull { doc ->
                        try {
                            val story = Story.fromMap(doc.data ?: emptyMap())
                            // Filter by expiration
                            if (story.expiresAt > currentTime && story.createdAt > twentyFourHoursAgo) {
                                story
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    // Update cache
                    if (stories.isNotEmpty()) {
                        storyDao.insertStories(stories)
                    }
                }

                // Fetch author
                val storiesWithAuthors = fetchAuthorsForStories(stories)

                Result.success(storiesWithAuthors)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Fetch a single story with its author
     * @param storyId Story ID to fetch
     * @return Result containing StoryWithAuthor
     */
    suspend fun fetchStoryWithAuthor(storyId: String): Result<StoryWithAuthor> =
        withContext(Dispatchers.IO) {
            try {
                // Check cache first
                var story = storyDao.getStoryById(storyId)

                // If not in cache, fetch from Firestore
                if (story == null) {
                    val document = storiesCollection.document(storyId).get().await()

                    if (document.exists()) {
                        story = Story.fromMap(document.data ?: emptyMap())
                        storyDao.insertStory(story)
                    } else {
                        return@withContext Result.failure(Exception("Story not found"))
                    }
                }

                // Check if story is still active
                if (!story.isActive()) {
                    return@withContext Result.failure(Exception("Story is no longer active"))
                }

                // Fetch author
                val authorResult = userExtractor.fetchCurrentUser(story.authorId)

                if (authorResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch story author: ${authorResult.exceptionOrNull()?.message}")
                    )
                }

                val author = authorResult.getOrThrow()
                val storyWithAuthor = StoryWithAuthor(story, author)

                Result.success(storyWithAuthor)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Increment view count for a story
     * @param storyId Story ID
     */
    suspend fun incrementViewCount(storyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Update in Firestore
            storiesCollection.document(storyId)
                .update("viewsCount", com.google.firebase.firestore.FieldValue.increment(1))
                .await()

            // Update in cache
            storyDao.incrementViewCount(storyId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        CacheStats(
            cachedStoryCount = storyDao.getActiveStoryCount(currentTime)
        )
    }

    /**
     * Clear all cached stories
     */
    suspend fun clearCache(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            storyDao.deleteAllStories()
            lastDocument = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reset pagination cursor
     */
    fun resetPagination() {
        lastDocument = null
    }

    /**
     * Fetch authors for a list of stories
     * Returns list of StoryWithAuthor
     */
    private suspend fun fetchAuthorsForStories(stories: List<Story>): List<StoryWithAuthor> =
        kotlinx.coroutines.coroutineScope {
            // Get unique author IDs
            val authorIds = stories.map { it.authorId }.distinct()

            // Fetch all authors concurrently
            val authorsMap = authorIds.map { authorId ->
                async {
                    val result = userExtractor.fetchCurrentUser(authorId)
                    if (result.isSuccess) {
                        authorId to result.getOrNull()
                    } else {
                        authorId to null
                    }
                }
            }.awaitAll().associate { it }

            // Combine stories with their authors
            stories.mapNotNull { story ->
                val author = authorsMap[story.authorId]
                if (author != null) {
                    StoryWithAuthor(story, author)
                } else {
                    null // Skip stories where author couldn't be fetched
                }
            }
        }

    /**
     * Clean up expired stories from cache
     */
    private suspend fun cleanupExpiredStories() {
        val currentTime = System.currentTimeMillis()
        storyDao.deleteExpiredStories(currentTime)
    }

    /**
     * Data class for cache statistics
     */
    data class CacheStats(
        val cachedStoryCount: Int
    )
}