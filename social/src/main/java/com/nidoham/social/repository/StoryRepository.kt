package com.nidoham.social.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
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
            // Generate ID if not provided
            val storyId = story.id ?: storiesCollection.document().id
            val storyWithId = story.copy(id = storyId)

            storiesCollection.document(storyId)
                .set(storyWithId)
                .await()

            Result.success(storyId)
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
     */
    suspend fun getActiveStories(limit: Int = 50): Result<List<Story>> {
        return try {
            val currentTime = System.currentTimeMillis()

            // Fetch all recent stories first
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
                        (story.metadata.expiresAt ?: 0L) > currentTime
            }.take(limit)

            Result.success(stories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Alternative: Get active stories with simpler query
     * Uses single field ordering to avoid complex index requirements
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
                        (story.metadata.expiresAt ?: 0L) > currentTime
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
            require(!story.id.isNullOrBlank()) { "Story ID cannot be null or blank" }

            val updatedStory = story.copy(
                metadata = story.metadata.markAsUpdated()
            )

            storiesCollection.document(story.id!!)
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
                "metadata.updatedAt" to System.currentTimeMillis() // Use Long for consistency
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
     */
    suspend fun incrementViewCount(storyId: String): Result<Unit> {
        return try {
            storiesCollection.document(storyId)
                .update("stats.viewCount", FieldValue.increment(1))
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
                .update(fieldPath, FieldValue.increment(1))
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
                .update(fieldPath, FieldValue.increment(-1))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Increment reply count
     */
    suspend fun incrementReplyCount(storyId: String): Result<Unit> {
        return try {
            storiesCollection.document(storyId)
                .update("stats.replyCount", FieldValue.increment(1))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Increment share count
     */
    suspend fun incrementShareCount(storyId: String): Result<Unit> {
        return try {
            storiesCollection.document(storyId)
                .update("stats.shareCount", FieldValue.increment(1))
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
                        (story.metadata.expiresAt ?: 0L) > currentTime
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

            // Query using Long timestamp
            val documents = storiesCollection
                .whereLessThan("metadata.expiresAt", currentTime)
                .get()
                .await()

            var deletedCount = 0
            // Batch delete for better performance
            val batch = firestore.batch()

            documents.forEach { document ->
                batch.delete(document.reference)
                deletedCount++
            }

            if (deletedCount > 0) {
                batch.commit().await()
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