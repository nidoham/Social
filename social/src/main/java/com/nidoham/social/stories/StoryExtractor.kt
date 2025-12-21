package com.nidoham.social.stories

import android.content.Context
import android.util.Log
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
        private const val TAG = "StoryExtractor"
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

            Log.d(TAG, "Successfully pushed story: ${story.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push story: ${story.id}", e)
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

            Log.d(TAG, "Successfully removed story: $storyId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove story: $storyId", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch paginated stories with their authors (20 per page)
     * Only returns stories from the last 24 hours
     * @param page Page number (0-indexed)
     * @param force If true, skip cache and fetch directly from Firestore
     * @return Result containing list of StoryWithAuthor
     */
    suspend fun fetchStoriesPage(
        page: Int,
        force: Boolean = false
    ): Result<List<StoryWithAuthor>> = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val twentyFourHoursAgo = currentTime - TWENTY_FOUR_HOURS_MS

            // Try cache first if not forcing refresh
            if (!force && page == 0) {
                val cachedStories = storyDao.getActiveStoriesPaginated(
                    currentTime = currentTime,
                    limit = PAGE_SIZE,
                    offset = 0
                )
                if (cachedStories.isNotEmpty()) {
                    val storiesWithAuthors = fetchAuthorsForStories(cachedStories)
                    if (storiesWithAuthors.isNotEmpty()) {
                        Log.d(TAG, "Returning ${storiesWithAuthors.size} stories from cache")
                        return@withContext Result.success(storiesWithAuthors)
                    }
                }
            }

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
                    Log.e(TAG, "Failed to parse story: ${doc.id}", e)
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

            Log.d(TAG, "Fetched ${storiesWithAuthors.size} stories from Firestore")
            Result.success(storiesWithAuthors)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch stories page: $page", e)

            // Fallback to cache on error
            try {
                val currentTime = System.currentTimeMillis()
                val cachedStories = storyDao.getActiveStoriesPaginated(
                    currentTime = currentTime,
                    limit = PAGE_SIZE,
                    offset = page * PAGE_SIZE
                )
                if (cachedStories.isNotEmpty()) {
                    val storiesWithAuthors = fetchAuthorsForStories(cachedStories)
                    Log.d(TAG, "Returning ${storiesWithAuthors.size} stories from cache (fallback)")
                    return@withContext Result.success(storiesWithAuthors)
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Cache fallback also failed", cacheError)
            }

            Result.failure(e)
        }
    }

    /**
     * Fetch stories by a specific author with author info
     * @param authorId Author's user ID
     * @param force If true, skip cache and fetch directly from Firestore
     * @return Result containing list of StoryWithAuthor
     */
    suspend fun fetchStoriesByAuthor(
        authorId: String,
        force: Boolean = false
    ): Result<List<StoryWithAuthor>> = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val twentyFourHoursAgo = currentTime - TWENTY_FOUR_HOURS_MS

            // Try cache first if not forcing refresh
            if (!force) {
                val cachedStories = storyDao.getStoriesByAuthor(authorId, currentTime)
                if (cachedStories.isNotEmpty()) {
                    val storiesWithAuthors = fetchAuthorsForStories(cachedStories)
                    if (storiesWithAuthors.isNotEmpty()) {
                        Log.d(TAG, "Returning ${storiesWithAuthors.size} stories for author $authorId from cache")
                        return@withContext Result.success(storiesWithAuthors)
                    }
                }
            }

            // Fetch from Firestore
            val querySnapshot = storiesCollection
                .whereEqualTo("authorId", authorId)
                .whereEqualTo("isDeleted", false)
                .whereEqualTo("isBanned", false)
                .whereGreaterThan("createdAt", twentyFourHoursAgo)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            // Parse and filter by expiration
            val stories = querySnapshot.documents.mapNotNull { doc ->
                try {
                    val story = Story.fromMap(doc.data ?: emptyMap())
                    // Filter by expiration
                    if (story.expiresAt > currentTime && story.createdAt > twentyFourHoursAgo) {
                        story
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse story: ${doc.id}", e)
                    null
                }
            }

            // Update cache
            if (stories.isNotEmpty()) {
                storyDao.insertStories(stories)
            }

            // Fetch author
            val storiesWithAuthors = fetchAuthorsForStories(stories)

            Log.d(TAG, "Fetched ${storiesWithAuthors.size} stories for author: $authorId from Firestore")
            Result.success(storiesWithAuthors)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch stories for author: $authorId", e)

            // Fallback to cache on error
            try {
                val currentTime = System.currentTimeMillis()
                val cachedStories = storyDao.getStoriesByAuthor(authorId, currentTime)
                val storiesWithAuthors = fetchAuthorsForStories(cachedStories)
                Log.d(TAG, "Returning ${storiesWithAuthors.size} stories from cache (fallback)")
                return@withContext Result.success(storiesWithAuthors)
            } catch (cacheError: Exception) {
                Log.e(TAG, "Cache fallback failed", cacheError)
            }

            Result.failure(e)
        }
    }

    /**
     * Fetch a single story with its author
     * @param storyId Story ID to fetch
     * @param force If true, skip cache and fetch directly from Firestore
     * @return Result containing StoryWithAuthor
     */
    suspend fun fetchStoryWithAuthor(
        storyId: String,
        force: Boolean = false
    ): Result<StoryWithAuthor> = withContext(Dispatchers.IO) {
        try {
            var story: Story? = null

            // Try cache first if not forcing refresh
            if (!force) {
                story = storyDao.getStoryById(storyId)
                if (story != null && story.isActive()) {
                    val authorResult = userExtractor.fetchCurrentUser(story.authorId)
                    if (authorResult.isSuccess) {
                        val author = authorResult.getOrThrow()
                        val storyWithAuthor = StoryWithAuthor(story, author)
                        Log.d(TAG, "Returning story $storyId from cache")
                        return@withContext Result.success(storyWithAuthor)
                    }
                }
            }

            // Fetch from Firestore
            val document = storiesCollection.document(storyId).get().await()

            if (!document.exists()) {
                return@withContext Result.failure(Exception("Story not found"))
            }

            story = Story.fromMap(document.data ?: emptyMap())

            // Check if story is still active
            if (!story.isActive()) {
                return@withContext Result.failure(Exception("Story is no longer active"))
            }

            // Update cache
            storyDao.insertStory(story)

            // Fetch author
            val authorResult = userExtractor.fetchCurrentUser(story.authorId)

            if (authorResult.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to fetch story author: ${authorResult.exceptionOrNull()?.message}")
                )
            }

            val author = authorResult.getOrThrow()
            val storyWithAuthor = StoryWithAuthor(story, author)

            Log.d(TAG, "Fetched story: $storyId from Firestore")
            Result.success(storyWithAuthor)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch story: $storyId", e)

            // Fallback to cache on error
            try {
                val cachedStory = storyDao.getStoryById(storyId)
                if (cachedStory != null && cachedStory.isActive()) {
                    val authorResult = userExtractor.fetchCurrentUser(cachedStory.authorId)
                    if (authorResult.isSuccess) {
                        val author = authorResult.getOrThrow()
                        val storyWithAuthor = StoryWithAuthor(cachedStory, author)
                        Log.d(TAG, "Returning story from cache (fallback)")
                        return@withContext Result.success(storyWithAuthor)
                    }
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Cache fallback failed", cacheError)
            }

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
            Log.e(TAG, "Failed to increment view count for story: $storyId", e)
            // Still update cache even if Firestore fails
            try {
                storyDao.incrementViewCount(storyId)
            } catch (cacheError: Exception) {
                Log.e(TAG, "Cache update also failed", cacheError)
            }
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
            Log.d(TAG, "Cleared cache")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
            Result.failure(e)
        }
    }

    /**
     * Reset pagination cursor
     */
    fun resetPagination() {
        lastDocument = null
        Log.d(TAG, "Reset pagination")
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
                    authorId to result.getOrNull()
                }
            }.awaitAll().toMap()

            // Combine stories with their authors
            stories.mapNotNull { story ->
                val author = authorsMap[story.authorId]
                if (author != null) {
                    StoryWithAuthor(story, author)
                } else {
                    Log.w(TAG, "Skipping story ${story.id} - author ${story.authorId} not found")
                    null
                }
            }
        }

    /**
     * Clean up expired stories from cache
     */
    private suspend fun cleanupExpiredStories() {
        try {
            val currentTime = System.currentTimeMillis()
            storyDao.deleteExpiredStories(currentTime)
            Log.d(TAG, "Cleaned up expired stories")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup expired stories", e)
        }
    }

    /**
     * Data class for cache statistics
     */
    data class CacheStats(
        val cachedStoryCount: Int
    )
}