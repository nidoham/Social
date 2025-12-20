package com.nidoham.social.posts

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nidoham.social.reaction.Reaction
import com.nidoham.social.user.UserExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


/**
 * PostExtractor handles post data operations with Firestore and Room caching
 * Implements cache-first strategy and fetches posts with their authors
 * Reactions are stored in subcollection: /posts/{postId}/reactions/{reactionType}
 */
class PostExtractor(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val postsCollection = firestore.collection("posts")
    private val database = PostDatabase.getInstance(context)
    private val postDao = database.postDao()
    private val userExtractor = UserExtractor(context)

    // Store last document for pagination
    private var lastDocument: DocumentSnapshot? = null

    companion object {
        private const val PAGE_SIZE = 20
    }

    /**
     * Push a post to Firestore and update cache
     * Reactions are stored in subcollection: /posts/{postId}/reactions/{reactionType}
     * @param post Post to push
     * @return Result indicating success or failure
     */
    suspend fun pushPost(post: Post): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Convert post to map for Firestore (without reactions)
            val postMap = hashMapOf(
                "id" to post.id,
                "authorId" to post.authorId,
                "content" to post.content,
                "contentType" to post.contentType,
                "mediaUrls" to post.mediaUrls,
                "createdAt" to post.createdAt,
                "updatedAt" to post.updatedAt,
                "visibility" to post.visibility,
                "location" to post.location,
                "commentsCount" to post.commentsCount,
                "sharesCount" to post.sharesCount,
                "viewsCount" to post.viewsCount,
                "isSponsored" to post.isSponsored,
                "isPinned" to post.isPinned,
                "isDeleted" to post.isDeleted,
                "isBanned" to post.isBanned,
                "status" to post.status,
                "hashtags" to post.hashtags,
                "mentions" to post.mentions
            )

            // Push post to Firestore
            postsCollection.document(post.id)
                .set(postMap)
                .await()

            // Push reactions to subcollection as individual documents
            val reactionsRef = postsCollection.document(post.id).collection("reactions")

            reactionsRef.document("likes").set(mapOf("value" to post.reactions.likes)).await()
            reactionsRef.document("loves").set(mapOf("value" to post.reactions.loves)).await()
            reactionsRef.document("hooray").set(mapOf("value" to post.reactions.hooray)).await()
            reactionsRef.document("wows").set(mapOf("value" to post.reactions.wows)).await()
            reactionsRef.document("kisses").set(mapOf("value" to post.reactions.kisses)).await()
            reactionsRef.document("emojis").set(mapOf("value" to post.reactions.emojis)).await()
            reactionsRef.document("angry").set(mapOf("value" to post.reactions.angry)).await()
            reactionsRef.document("sad").set(mapOf("value" to post.reactions.sad)).await()
            reactionsRef.document("heart").set(mapOf("value" to post.reactions.heart)).await()
            reactionsRef.document("laugh").set(mapOf("value" to post.reactions.laugh)).await()
            reactionsRef.document("confused").set(mapOf("value" to post.reactions.confused)).await()
            reactionsRef.document("eyes").set(mapOf("value" to post.reactions.eyes)).await()
            reactionsRef.document("heartEyes").set(mapOf("value" to post.reactions.heartEyes)).await()
            reactionsRef.document("fire").set(mapOf("value" to post.reactions.fire)).await()

            // Update cache (reactions not stored in Room)
            postDao.insertPost(post)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a post from Firestore and cache
     * @param postId Post ID to remove
     * @return Result indicating success or failure
     */
    suspend fun removePost(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Remove reactions subcollection
            val reactionsRef = postsCollection.document(postId).collection("reactions")
            val reactionTypes = listOf("likes", "loves", "hooray", "wows", "kisses", "emojis",
                "angry", "sad", "heart", "laugh", "confused", "eyes", "heartEyes", "fire")

            reactionTypes.forEach { type ->
                reactionsRef.document(type).delete().await()
            }

            // Remove post from Firestore
            postsCollection.document(postId)
                .delete()
                .await()

            // Remove from cache
            postDao.deletePostById(postId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch paginated posts with their authors (20 per page)
     * Always fetches from Firestore to get latest reactions
     * @param page Page number (0-indexed)
     * @return Result containing list of PostWithAuthor
     */
    suspend fun fetchPostsPage(page: Int): Result<List<PostWithAuthor>> = withContext(Dispatchers.IO) {
        try {
            // Reset pagination if requesting first page
            if (page == 0) {
                lastDocument = null
            }

            // Build Firestore query
            var query: Query = postsCollection
                .whereEqualTo("isDeleted", false)
                .whereEqualTo("isBanned", false)
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

            // Parse posts from Firestore and fetch their reactions
            val posts = querySnapshot.documents.mapNotNull { doc ->
                try {
                    convertDocumentToPost(doc)
                } catch (e: Exception) {
                    null
                }
            }

            // Update cache (reactions not stored in Room)
            if (posts.isNotEmpty()) {
                postDao.insertPosts(posts)
            }

            // Fetch authors for posts
            val postsWithAuthors = fetchAuthorsForPosts(posts)

            Result.success(postsWithAuthors)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch posts by a specific author with author info
     * @param authorId Author's user ID
     * @return Result containing list of PostWithAuthor
     */
    suspend fun fetchPostsByAuthor(authorId: String): Result<List<PostWithAuthor>> =
        withContext(Dispatchers.IO) {
            try {
                // Fetch from Firestore
                val querySnapshot = postsCollection
                    .whereEqualTo("authorId", authorId)
                    .whereEqualTo("isDeleted", false)
                    .whereEqualTo("isBanned", false)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val posts = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        convertDocumentToPost(doc)
                    } catch (e: Exception) {
                        null
                    }
                }

                // Update cache (reactions not stored in Room)
                if (posts.isNotEmpty()) {
                    postDao.insertPosts(posts)
                }

                // Fetch authors
                val postsWithAuthors = fetchAuthorsForPosts(posts)

                Result.success(postsWithAuthors)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Fetch a single post with its author
     * @param postId Post ID to fetch
     * @return Result containing PostWithAuthor
     */
    suspend fun fetchPostWithAuthor(postId: String): Result<PostWithAuthor> =
        withContext(Dispatchers.IO) {
            try {
                // Fetch from Firestore (always get latest reactions)
                val document = postsCollection.document(postId).get().await()

                if (!document.exists()) {
                    return@withContext Result.failure(Exception("Post not found"))
                }

                val post = convertDocumentToPost(document)
                if (post == null) {
                    return@withContext Result.failure(Exception("Failed to parse post"))
                }

                // Check if post is deleted or banned
                if (post.isDeleted || post.isBanned) {
                    return@withContext Result.failure(Exception("Post is not available"))
                }

                // Update cache (reactions not stored in Room)
                postDao.insertPost(post)

                // Fetch author
                val authorResult = userExtractor.fetchCurrentUser(post.authorId)

                if (authorResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch post author: ${authorResult.exceptionOrNull()?.message}")
                    )
                }

                val author = authorResult.getOrThrow()
                val postWithAuthor = PostWithAuthor(post, author)

                Result.success(postWithAuthor)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Increment view count for a post
     * @param postId Post ID
     */
    suspend fun incrementViewCount(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Update in Firestore
            postsCollection.document(postId)
                .update("viewsCount", com.google.firebase.firestore.FieldValue.increment(1))
                .await()

            // Update in cache
            postDao.incrementViewCount(postId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update reaction for a post
     * Reactions are stored in: /posts/{postId}/reactions/{reactionType}
     * @param postId Post ID
     * @param reactionType Type of reaction (likes, loves, wows, etc.)
     * @param increment Amount to increment (positive or negative)
     */
    suspend fun updateReaction(postId: String, reactionType: String, increment: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Update in Firestore subcollection
                postsCollection.document(postId)
                    .collection("reactions")
                    .document(reactionType)
                    .update("value", com.google.firebase.firestore.FieldValue.increment(increment.toLong()))
                    .await()

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Fetch reactions for a specific post
     * @param postId Post ID
     * @return Result containing Reaction object
     */
    suspend fun fetchReactions(postId: String): Result<Reaction> = withContext(Dispatchers.IO) {
        try {
            val reactionsRef = postsCollection.document(postId).collection("reactions")
            val reactionDocs = reactionsRef.get().await()

            var reactions = Reaction()

            reactionDocs.documents.forEach { reactionDoc ->
                val value = reactionDoc.getLong("value")?.toInt() ?: 0
                reactions = when (reactionDoc.id) {
                    "likes" -> reactions.copy(likes = value)
                    "loves" -> reactions.copy(loves = value)
                    "hooray" -> reactions.copy(hooray = value)
                    "wows" -> reactions.copy(wows = value)
                    "kisses" -> reactions.copy(kisses = value)
                    "emojis" -> reactions.copy(emojis = value)
                    "angry" -> reactions.copy(angry = value)
                    "sad" -> reactions.copy(sad = value)
                    "heart" -> reactions.copy(heart = value)
                    "laugh" -> reactions.copy(laugh = value)
                    "confused" -> reactions.copy(confused = value)
                    "eyes" -> reactions.copy(eyes = value)
                    "heartEyes" -> reactions.copy(heartEyes = value)
                    "fire" -> reactions.copy(fire = value)
                    else -> reactions
                }
            }

            Result.success(reactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        CacheStats(
            cachedPostCount = postDao.getActivePostCount()
        )
    }

    /**
     * Clear all cached posts
     */
    suspend fun clearCache(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            postDao.deleteAllPosts()
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
     * Convert Firestore document to Post
     * Fetches reactions from subcollection: /posts/{postId}/reactions/{reactionType}
     */
    private suspend fun convertDocumentToPost(doc: DocumentSnapshot): Post? {
        try {
            val mediaUrls = doc.get("mediaUrls") as? List<*>
            val hashtags = doc.get("hashtags") as? List<*>
            val mentions = doc.get("mentions") as? List<*>

            // Fetch all reaction documents from subcollection
            val reactionsRef = postsCollection.document(doc.id).collection("reactions")
            val reactionDocs = reactionsRef.get().await()

            var reactions = Reaction()

            reactionDocs.documents.forEach { reactionDoc ->
                val value = reactionDoc.getLong("value")?.toInt() ?: 0
                reactions = when (reactionDoc.id) {
                    "likes" -> reactions.copy(likes = value)
                    "loves" -> reactions.copy(loves = value)
                    "hooray" -> reactions.copy(hooray = value)
                    "wows" -> reactions.copy(wows = value)
                    "kisses" -> reactions.copy(kisses = value)
                    "emojis" -> reactions.copy(emojis = value)
                    "angry" -> reactions.copy(angry = value)
                    "sad" -> reactions.copy(sad = value)
                    "heart" -> reactions.copy(heart = value)
                    "laugh" -> reactions.copy(laugh = value)
                    "confused" -> reactions.copy(confused = value)
                    "eyes" -> reactions.copy(eyes = value)
                    "heartEyes" -> reactions.copy(heartEyes = value)
                    "fire" -> reactions.copy(fire = value)
                    else -> reactions
                }
            }

            return Post.create(
                id = doc.id,
                authorId = doc.getString("authorId") ?: "",
                content = doc.getString("content") ?: "",
                contentType = doc.getString("contentType") ?: "text",
                mediaUrls = mediaUrls?.mapNotNull { it as? String } ?: emptyList(),
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                visibility = doc.getString("visibility") ?: "public",
                location = doc.getString("location"),
                reactions = reactions,
                commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0,
                sharesCount = doc.getLong("sharesCount")?.toInt() ?: 0,
                viewsCount = doc.getLong("viewsCount")?.toInt() ?: 0,
                isSponsored = doc.getBoolean("isSponsored") ?: false,
                isPinned = doc.getBoolean("isPinned") ?: false,
                isDeleted = doc.getBoolean("isDeleted") ?: false,
                isBanned = doc.getBoolean("isBanned") ?: false,
                status = doc.getString("status") ?: "active",
                hashtags = hashtags?.mapNotNull { it as? String } ?: emptyList(),
                mentions = mentions?.mapNotNull { it as? String } ?: emptyList()
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Fetch authors for a list of posts
     * Returns list of PostWithAuthor
     */
    private suspend fun fetchAuthorsForPosts(posts: List<Post>): List<PostWithAuthor> =
        kotlinx.coroutines.coroutineScope {
            // Get unique author IDs
            val authorIds = posts.map { it.authorId }.distinct()

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

            // Combine posts with their authors
            posts.mapNotNull { post ->
                val author = authorsMap[post.authorId]
                if (author != null) {
                    PostWithAuthor(post, author)
                } else {
                    null // Skip posts where author couldn't be fetched
                }
            }
        }

    /**
     * Data class for cache statistics
     */
    data class CacheStats(
        val cachedPostCount: Int
    )
}