package com.nidoham.social.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nidoham.social.model.*
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing story data in Firestore.
 * Handles all CRUD operations and queries for stories.
 */
class StoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // Collection reference
    private val storiesCollection = firestore.collection(COLLECTION_STORIES)

    /**
     * Create a new story
     */
    suspend fun createStory(story: Story): Result<String> {
        return try {
            storiesCollection.document(story.id)
                .set(story)
                .await()

            Result.success(story.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get story by ID
     */
    suspend fun getStoryById(storyId: String): Result<Story?> {
        return try {
            val document = storiesCollection.document(storyId).get().await()
            val story = document.toObject(Story::class.java)
            Result.success(story)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all active stories (not expired, not deleted, not banned)
     * Ordered by creation time (newest first)
     *
     * FIXED: Simplified query to avoid complex index requirements
     */
    suspend fun getActiveStories(limit: Int = 50): Result<List<Story>> {
        return try {
            val currentTime = System.currentTimeMillis()

            // Fetch all recent stories first, then filter in-memory
            val documents = storiesCollection
                .orderBy("metadata.createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong() * 2) // Fetch more to account for filtering
                .get()
                .await()

            // Filter active stories in-memory
            val stories = documents.mapNotNull { doc ->
                doc.toObject(Story::class.java)
            }.filter { story ->
                !story.metadata.isDeleted &&
                        !story.metadata.isBanned &&
                        story.metadata.expiresAt > currentTime
            }.take(limit) // Take only the requested limit after filtering

            Result.success(stories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Alternative: Get active stories with simpler query
     * Uses single field ordering to avoid index requirements
     */
    suspend fun getActiveStoriesSimple(limit: Int = 50): Result<List<Story>> {
        return try {
            val currentTime = System.currentTimeMillis()

            // Query only by expiresAt to avoid complex indexes
            val documents = storiesCollection
                .whereGreaterThan("metadata.expiresAt", currentTime)
                .orderBy("metadata.expiresAt", Query.Direction.DESCENDING)
                .limit(limit.toLong() * 2)
                .get()
                .await()

            // Filter in-memory for deleted and banned
            val stories = documents.mapNotNull { doc ->
                doc.toObject(Story::class.java)
            }.filter { story ->
                !story.metadata.isDeleted && !story.metadata.isBanned
            }.take(limit)

            Result.success(stories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get stories by author ID
     * FIXED: Simplified to single orderBy
     */
    suspend fun getStoriesByAuthor(authorId: String, limit: Int = 20): Result<List<Story>> {
        return try {
            val documents = storiesCollection
                .whereEqualTo("authorId", authorId)
                .orderBy("metadata.createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val stories = documents.mapNotNull { it.toObject(Story::class.java) }
            Result.success(stories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get active stories by author ID (not expired, not deleted)
     * FIXED: In-memory filtering to avoid complex indexes
     */
    suspend fun getActiveStoriesByAuthor(authorId: String): Result<List<Story>> {
        return try {
            val currentTime = System.currentTimeMillis()

            val documents = storiesCollection
                .whereEqualTo("authorId", authorId)
                .orderBy("metadata.createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            // Filter active stories in-memory
            val stories = documents.mapNotNull { doc ->
                doc.toObject(Story::class.java)
            }.filter { story ->
                !story.metadata.isDeleted &&
                        story.metadata.expiresAt > currentTime
            }

            Result.success(stories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update story
     */
    suspend fun updateStory(story: Story): Result<Unit> {
        return try {
            val updatedStory = story.copy(
                metadata = story.metadata.markAsUpdated()
            )

            storiesCollection.document(story.id)
                .set(updatedStory)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update story caption
     */
    suspend fun updateCaption(storyId: String, caption: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "caption" to caption,
                "metadata.isEdited" to true,
                "metadata.updatedAt" to System.currentTimeMillis()
            )

            storiesCollection.document(storyId)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Increment view count
     * FIXED: Use FieldValue.increment() instead of read-then-write
     */
    suspend fun incrementViewCount(storyId: String): Result<Unit> {
        return try {
            storiesCollection.document(storyId)
                .update("stats.viewCount", com.google.firebase.firestore.FieldValue.increment(1))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add or update reaction
     */
    suspend fun addReaction(storyId: String, reactionType: ReactionType): Result<Unit> {
        return try {
            val fieldPath = "stats.reactionCounts.${reactionType.name}"
            storiesCollection.document(storyId)
                .update(fieldPath, com.google.firebase.firestore.FieldValue.increment(1))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove reaction
     */
    suspend fun removeReaction(storyId: String, reactionType: ReactionType): Result<Unit> {
        return try {
            val fieldPath = "stats.reactionCounts.${reactionType.name}"
            storiesCollection.document(storyId)
                .update(fieldPath, com.google.firebase.firestore.FieldValue.increment(-1))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Increment reply count
     * FIXED: Use FieldValue.increment()
     */
    suspend fun incrementReplyCount(storyId: String): Result<Unit> {
        return try {
            storiesCollection.document(storyId)
                .update("stats.replyCount", com.google.firebase.firestore.FieldValue.increment(1))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Increment share count
     * FIXED: Use FieldValue.increment()
     */
    suspend fun incrementShareCount(storyId: String): Result<Unit> {
        return try {
            storiesCollection.document(storyId)
                .update("stats.shareCount", com.google.firebase.firestore.FieldValue.increment(1))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Soft delete story (mark as deleted)
     */
    suspend fun deleteStory(storyId: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "metadata.isDeleted" to true,
                "metadata.updatedAt" to System.currentTimeMillis()
            )

            storiesCollection.document(storyId)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hard delete story (permanently remove)
     */
    suspend fun permanentlyDeleteStory(storyId: String): Result<Unit> {
        return try {
            storiesCollection.document(storyId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ban story (admin action)
     */
    suspend fun banStory(storyId: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "metadata.isBanned" to true,
                "metadata.updatedAt" to System.currentTimeMillis()
            )

            storiesCollection.document(storyId)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get stories by visibility type
     * FIXED: Simplified query with in-memory filtering
     */
    suspend fun getStoriesByVisibility(
        visibility: StoryVisibility,
        limit: Int = 50
    ): Result<List<Story>> {
        return try {
            val currentTime = System.currentTimeMillis()

            val documents = storiesCollection
                .whereEqualTo("metadata.visibility", visibility.name)
                .orderBy("metadata.createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong() * 2)
                .get()
                .await()

            // Filter in-memory for active stories
            val stories = documents.mapNotNull { doc ->
                doc.toObject(Story::class.java)
            }.filter { story ->
                !story.metadata.isDeleted &&
                        story.metadata.expiresAt > currentTime
            }.take(limit)

            Result.success(stories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete expired stories (cleanup task)
     */
    suspend fun deleteExpiredStories(): Result<Int> {
        return try {
            val currentTime = System.currentTimeMillis()

            val documents = storiesCollection
                .whereLessThan("metadata.expiresAt", currentTime)
                .get()
                .await()

            var deletedCount = 0
            documents.forEach { document ->
                document.reference.delete().await()
                deletedCount++
            }

            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get stories count by author
     */
    suspend fun getStoriesCountByAuthor(authorId: String): Result<Int> {
        return try {
            val documents = storiesCollection
                .whereEqualTo("authorId", authorId)
                .get()
                .await()

            Result.success(documents.size())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if user can view story based on visibility settings
     */
    suspend fun canUserViewStory(storyId: String, userId: String): Result<Boolean> {
        return try {
            val result = getStoryById(storyId)
            result.getOrNull()?.let { story ->
                val canView = story.metadata.canView(userId, story.authorId)
                Result.success(canView)
            } ?: Result.failure(Exception("Story not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val COLLECTION_STORIES = "stories"
    }
}