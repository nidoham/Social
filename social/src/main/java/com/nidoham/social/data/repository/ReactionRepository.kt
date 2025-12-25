package com.nidoham.social.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nidoham.social.data.local.AppDatabase
import com.nidoham.social.reaction.ReactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReactionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase
) {
    private val postDao = database.postDao()
    private val storyDao = database.storyDao()

    companion object {
        private const val TAG = "ReactionRepo"
    }

    suspend fun reactToPost(postId: String, userId: String, type: ReactionType): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            postDao.incrementLikeCount(postId)
            updateFirestoreReaction("posts", postId, userId, type)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Reaction failed", e)
            Result.failure(e)
        }
    }

    suspend fun reactToStory(storyId: String, userId: String, type: ReactionType): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            storyDao.incrementReactionCount(storyId)
            updateFirestoreReaction("stories", storyId, userId, type)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateFirestoreReaction(collectionName: String, contentId: String, userId: String, reactionType: ReactionType) {
        val batch = firestore.batch()
        val userReactionRef = firestore.collection(collectionName).document(contentId).collection("reactions").document(userId)
        val contentRef = firestore.collection(collectionName).document(contentId)

        val doc = userReactionRef.get().await()
        val previousTypeStr = doc.getString("type")

        if (previousTypeStr != null) {
            val previousType = ReactionType.fromKey(previousTypeStr)
            if (previousType == reactionType) {
                batch.delete(userReactionRef)
                batch.update(contentRef, "reactions.${previousType.key}", FieldValue.increment(-1))
            } else {
                batch.set(userReactionRef, mapOf("type" to reactionType.key, "timestamp" to FieldValue.serverTimestamp()))
                batch.update(contentRef, "reactions.${previousType.key}", FieldValue.increment(-1))
                batch.update(contentRef, "reactions.${reactionType.key}", FieldValue.increment(1))
            }
        } else {
            batch.set(userReactionRef, mapOf("type" to reactionType.key, "timestamp" to FieldValue.serverTimestamp()))
            batch.update(contentRef, "reactions.${reactionType.key}", FieldValue.increment(1))
        }
        batch.commit().await()
    }
}