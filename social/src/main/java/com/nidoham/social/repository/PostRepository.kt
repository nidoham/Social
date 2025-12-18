package com.nidoham.social.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import com.nidoham.social.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PostRepository private constructor(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val postsCollection = firestore.collection("posts")

    companion object {
        @Volatile
        private var INSTANCE: PostRepository? = null

        fun getInstance(): PostRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PostRepository().also { INSTANCE = it }
            }
        }
    }

    // 🔥 CREATE / PUSH
    suspend fun createPost(post: Post): Result<String> = withContext(Dispatchers.IO) {
        try {
            val docRef = if (post.id.isNotBlank()) {
                postsCollection.document(post.id)
            } else {
                postsCollection.document()
            }

            val isNew = post.id.isBlank()
            val finalPost = if (isNew) {
                post.copy(
                    id = docRef.id,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            } else {
                post.copy(updatedAt = Timestamp.now())
            }

            docRef.set(finalPost).await()
            Result.success(finalPost.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 READ SINGLE
    suspend fun getPost(postId: String): Result<Post?> = withContext(Dispatchers.IO) {
        try {
            val doc = postsCollection.document(postId).get().await()
            Result.success(doc.toObject(Post::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 PAGINATED FEED (Page-based)
    fun getPaginatedFeed(
        pageSize: Long = 20,
        lastVisible: DocumentSnapshot? = null
    ): Flow<PagingResult<List<Post>>> = callbackFlow {

        // 1. Build Query
        var query = postsCollection
            .whereEqualTo("status", PostStatus.ACTIVE)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize)

        // 2. Apply Cursor if exists
        if (lastVisible != null) {
            query = query.startAfter(lastVisible)
        }

        // 3. Attach Listener
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(PagingResult.Error(error))
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val posts = snapshot.toObjects(Post::class.java)
                val newLastVisible = snapshot.documents.lastOrNull()
                trySend(PagingResult.Success(posts, newLastVisible))
            } else {
                // Return empty list instead of Error if just no more data
                trySend(PagingResult.Success(emptyList(), null))
            }
        }

        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO) // Run flow on IO thread

    // 🔥 UPDATED PAGING RESULT (Sealed Class is safer)
    sealed class PagingResult<out T> {
        data class Success<T>(
            val data: T,
            val lastVisible: DocumentSnapshot? = null
        ) : PagingResult<T>()

        data class Error(val exception: Throwable) : PagingResult<Nothing>()
    }

    // 🔥 REACTIONS (Transaction Safe)
    suspend fun toggleReaction(
        postId: String,
        reactionType: ReactionType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val postRef = postsCollection.document(postId)

            firestore.runTransaction { transaction ->
                // CRITICAL FIX: Use transaction.get(), NOT .get().await()
                val snapshot = transaction.get(postRef)

                // If post doesn't exist, throw to exit transaction
                val post = snapshot.toObject(Post::class.java)
                    ?: throw FirebaseFirestoreException("Post not found", FirebaseFirestoreException.Code.NOT_FOUND)

                val currentCount = post.reactionCounts[reactionType] ?: 0L

                // LOGIC NOTE: This simplistic toggle flips between 0 and 1.
                // For a real app, you usually increment (+1) or decrement (-1) based on user state.
                val newCount = if (currentCount > 0L) 0L else 1L
                val fieldPath = "reactionCounts.${reactionType.name}"

                transaction.update(postRef, fieldPath, newCount)
            }.await() // await the result of the transaction block

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 UPDATE COUNTERS
    suspend fun updateCounters(
        postId: String,
        commentCount: Long? = null,
        shareCount: Long? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (commentCount == null && shareCount == null) return@withContext Result.success(Unit)

            val updates = mutableMapOf<String, Any>()
            commentCount?.let { updates["commentCount"] = it }
            shareCount?.let { updates["shareCount"] = it }
            updates["updatedAt"] = Timestamp.now()

            postsCollection.document(postId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 DELETE (Soft Delete)
    suspend fun deletePost(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            postsCollection.document(postId)
                .update(
                    mapOf(
                        "status" to PostStatus.DELETED,
                        "deletedAt" to Timestamp.now()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 SEARCH BY HASHTAG
    fun searchByHashtag(hashtag: String): Flow<List<Post>> = callbackFlow {
        val listener = postsCollection
            .whereArrayContains("hashtags", hashtag)
            .whereEqualTo("status", PostStatus.ACTIVE)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList()) // Or send an error state
                    return@addSnapshotListener
                }
                val posts = snapshot?.toObjects(Post::class.java) ?: emptyList()
                trySend(posts)
            }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)
}