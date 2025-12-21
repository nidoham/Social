package com.nidoham.social.reaction

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * ReactionExtractor handles reaction operations with Firestore.
 * Always fetches from Firebase (no caching) to ensure real-time data.
 *
 * Firestore structure:
 * /reactions/{type}/{postId} - (reaction counts: likes, dislikes, etc.)
 * /activities/reactions/{type}/{postId}/{userId} - (user's reaction type)
 */
class ReactionExtractor {

    private val firestore = FirebaseFirestore.getInstance()
    private val reactionsCollection = firestore.collection("reactions")
    private val activitiesCollection = firestore.collection("activities")
        .document("reactions")

    companion object {
        private const val TAG = "ReactionExtractor"

        /**
         * Valid reaction types (content types)
         */
        object ContentType {
            const val POSTS = "posts"
            const val COMMENTS = "comments"
            const val STORIES = "stories"
            const val REPLIES = "replies"
        }
    }

    /**
     * Add or update a user's reaction
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @param userId ID of the user reacting
     * @param reactionType Type of reaction
     * @return Result indicating success or failure
     */
    suspend fun addReaction(
        type: String,
        postId: String,
        userId: String,
        reactionType: Reaction.Type
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val batch = firestore.batch()

            // Reference to reaction counts document
            val reactionRef = reactionsCollection
                .document(type)
                .collection(postId)
                .document("counts")

            // Reference to user activity document
            val userActivityRef = activitiesCollection
                .collection(type)
                .document(postId)
                .collection("users")
                .document(userId)

            // Check if user already has a reaction
            val existingActivity = userActivityRef.get().await()
            val previousReactionType = existingActivity.getString("reactionType")

            // If user had a different reaction, decrement the old one
            if (previousReactionType != null && previousReactionType != reactionType.key) {
                val oldType = Reaction.Type.fromKey(previousReactionType)
                if (oldType != null) {
                    batch.set(
                        reactionRef,
                        mapOf(oldType.key to FieldValue.increment(-1)),
                        SetOptions.merge()
                    )
                }
            }

            // Increment the new reaction count (only if it's a new reaction or different type)
            if (previousReactionType != reactionType.key) {
                batch.set(
                    reactionRef,
                    mapOf(reactionType.key to FieldValue.increment(1)),
                    SetOptions.merge()
                )
            }

            // Set user's new reaction in activities
            val userActivityData = mapOf(
                "userId" to userId,
                "reactionType" to reactionType.key,
                "timestamp" to System.currentTimeMillis()
            )
            batch.set(userActivityRef, userActivityData, SetOptions.merge())

            // Commit batch
            batch.commit().await()

            Log.d(TAG, "Successfully added reaction: $reactionType for $type/$postId by $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add reaction for $type/$postId", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a user's reaction
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @param userId ID of the user
     * @return Result indicating success or failure
     */
    suspend fun removeReaction(
        type: String,
        postId: String,
        userId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val batch = firestore.batch()

            // Reference to reaction counts document
            val reactionRef = reactionsCollection
                .document(type)
                .collection(postId)
                .document("counts")

            // Reference to user activity document
            val userActivityRef = activitiesCollection
                .collection(type)
                .document(postId)
                .collection("users")
                .document(userId)

            // Get existing reaction
            val existingActivity = userActivityRef.get().await()
            if (!existingActivity.exists()) {
                return@withContext Result.failure(Exception("No reaction found to remove"))
            }

            val reactionTypeKey = existingActivity.getString("reactionType")
            if (reactionTypeKey != null) {
                // Decrement the reaction count
                batch.set(
                    reactionRef,
                    mapOf(reactionTypeKey to FieldValue.increment(-1)),
                    SetOptions.merge()
                )
            }

            // Delete user's activity
            batch.delete(userActivityRef)

            // Commit batch
            batch.commit().await()

            Log.d(TAG, "Successfully removed reaction for $type/$postId by $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove reaction for $type/$postId", e)
            Result.failure(e)
        }
    }

    /**
     * Toggle a user's reaction (add if not exists, remove if exists with same type)
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @param userId ID of the user
     * @param reactionType Type of reaction
     * @return Result with true if added, false if removed
     */
    suspend fun toggleReaction(
        type: String,
        postId: String,
        userId: String,
        reactionType: Reaction.Type
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Check if user already has this exact reaction
            val userActivityRef = activitiesCollection
                .collection(type)
                .document(postId)
                .collection("users")
                .document(userId)

            val existingActivity = userActivityRef.get().await()
            val currentReactionType = existingActivity.getString("reactionType")

            if (currentReactionType == reactionType.key) {
                // Remove the reaction
                removeReaction(type, postId, userId)
                Result.success(false)
            } else {
                // Add or update the reaction
                addReaction(type, postId, userId, reactionType)
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle reaction for $type/$postId", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch reaction counts for a post (always from Firebase)
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @return Result containing Reaction object with counts
     */
    suspend fun fetchReactions(
        type: String,
        postId: String
    ): Result<Reaction> = withContext(Dispatchers.IO) {
        try {
            val countsDoc = reactionsCollection
                .document(type)
                .collection(postId)
                .document("counts")
                .get()
                .await()

            val reaction = if (countsDoc.exists()) {
                Reaction.fromMap(countsDoc.data ?: emptyMap())
            } else {
                Reaction.empty()
            }

            Log.d(TAG, "Fetched reactions for $type/$postId: ${reaction.total} total")
            Result.success(reaction)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch reactions for $type/$postId", e)
            Result.failure(e)
        }
    }

    /**
     * Get a specific user's reaction on a post
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @param userId ID of the user
     * @return Result containing the user's reaction type or null if no reaction
     */
    suspend fun getUserReaction(
        type: String,
        postId: String,
        userId: String
    ): Result<Reaction.Type?> = withContext(Dispatchers.IO) {
        try {
            val userActivityDoc = activitiesCollection
                .collection(type)
                .document(postId)
                .collection("users")
                .document(userId)
                .get()
                .await()

            val reactionType = if (userActivityDoc.exists()) {
                val reactionKey = userActivityDoc.getString("reactionType")
                reactionKey?.let { Reaction.Type.fromKey(it) }
            } else {
                null
            }

            Log.d(TAG, "User $userId reaction on $type/$postId: $reactionType")
            Result.success(reactionType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user reaction for $type/$postId", e)
            Result.failure(e)
        }
    }

    /**
     * Get all users who reacted with a specific reaction type
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @param reactionType Type of reaction to filter by
     * @param limit Maximum number of users to return
     * @return Result containing list of user IDs
     */
    suspend fun getUsersByReactionType(
        type: String,
        postId: String,
        reactionType: Reaction.Type,
        limit: Int = 50
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val querySnapshot = activitiesCollection
                .collection(type)
                .document(postId)
                .collection("users")
                .whereEqualTo("reactionType", reactionType.key)
                .limit(limit.toLong())
                .get()
                .await()

            val userIds = querySnapshot.documents.mapNotNull { it.id }

            Log.d(TAG, "Found ${userIds.size} users with $reactionType on $type/$postId")
            Result.success(userIds)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get users by reaction type for $type/$postId", e)
            Result.failure(e)
        }
    }

    /**
     * Get total reaction count for a post (sum of all reaction types)
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @return Result containing total count
     */
    suspend fun getTotalReactionCount(
        type: String,
        postId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val result = fetchReactions(type, postId)
            if (result.isSuccess) {
                val reaction = result.getOrThrow()
                Result.success(reaction.total)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch reactions"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get total reaction count for $type/$postId", e)
            Result.failure(e)
        }
    }

    /**
     * Delete all reactions for a post (useful when deleting content)
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @return Result indicating success or failure
     */
    suspend fun deleteAllReactions(
        type: String,
        postId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val batch = firestore.batch()

            // Delete counts document
            val countsRef = reactionsCollection
                .document(type)
                .collection(postId)
                .document("counts")
            batch.delete(countsRef)

            // Get all user activity documents
            val userActivitiesSnapshot = activitiesCollection
                .collection(type)
                .document(postId)
                .collection("users")
                .get()
                .await()

            // Delete each user activity
            userActivitiesSnapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            // Commit batch
            batch.commit().await()

            Log.d(TAG, "Successfully deleted all reactions for $type/$postId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all reactions for $type/$postId", e)
            Result.failure(e)
        }
    }

    /**
     * Get count of users who reacted (distinct users)
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @return Result containing user count
     */
    suspend fun getReactionUserCount(
        type: String,
        postId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val snapshot = activitiesCollection
                .collection(type)
                .document(postId)
                .collection("users")
                .get()
                .await()

            val userCount = snapshot.size()
            Log.d(TAG, "Total users who reacted on $type/$postId: $userCount")
            Result.success(userCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get reaction user count for $type/$postId", e)
            Result.failure(e)
        }
    }

    /**
     * Data class for reaction statistics
     */
    data class ReactionStats(
        val totalReactions: Int,
        val totalUsers: Int,
        val topReactions: List<Pair<Reaction.Type, Int>>
    )

    /**
     * Get comprehensive reaction statistics
     * @param type Content type (posts, comments, etc.)
     * @param postId ID of the content
     * @return Result containing ReactionStats
     */
    suspend fun getReactionStats(
        type: String,
        postId: String
    ): Result<ReactionStats> = withContext(Dispatchers.IO) {
        try {
            // Fetch reaction counts
            val reactionResult = fetchReactions(type, postId)
            if (reactionResult.isFailure) {
                return@withContext Result.failure(
                    reactionResult.exceptionOrNull() ?: Exception("Failed to fetch reactions")
                )
            }

            val reaction = reactionResult.getOrThrow()

            // Get total number of users who reacted
            val userCountResult = getReactionUserCount(type, postId)
            val userCount = userCountResult.getOrElse { 0 }

            val stats = ReactionStats(
                totalReactions = reaction.total,
                totalUsers = userCount,
                topReactions = reaction.getTopReactions(3)
            )

            Log.d(TAG, "Reaction stats for $type/$postId: ${stats.totalUsers} users, ${stats.totalReactions} reactions")
            Result.success(stats)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get reaction stats for $type/$postId", e)
            Result.failure(e)
        }
    }
}