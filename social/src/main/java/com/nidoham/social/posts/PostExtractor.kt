package com.nidoham.social.posts

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.nidoham.social.user.UserExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * PostExtractor handles post data operations with Firestore and Room caching
 * Implements cache-first strategy and fetches posts with their authors
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
        private const val TAG = "PostExtractor"
        private const val PAGE_SIZE = 20
        private const val MAX_RETRY_ATTEMPTS = 3

        // Firestore field names
        private object Fields {
            const val ID = "id"
            const val AUTHOR_ID = "authorId"
            const val CONTENT = "content"
            const val CONTENT_TYPE = "contentType"
            const val MEDIA_URLS = "mediaUrls"
            const val CREATED_AT = "createdAt"
            const val UPDATED_AT = "updatedAt"
            const val VISIBILITY = "visibility"
            const val LOCATION = "location"
            const val COMMENTS_COUNT = "commentsCount"
            const val SHARES_COUNT = "sharesCount"
            const val VIEWS_COUNT = "viewsCount"
            const val IS_SPONSORED = "isSponsored"
            const val IS_PINNED = "isPinned"
            const val IS_DELETED = "isDeleted"
            const val IS_BANNED = "isBanned"
            const val STATUS = "status"
            const val HASHTAGS = "hashtags"
            const val MENTIONS = "mentions"
        }
    }

    /**
     * Push a post to Firestore and update cache
     * @param post Post to push
     * @return Result indicating success or failure
     */
    suspend fun pushPost(post: Post): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val postMap = buildPostMap(post)

            // Push post to Firestore
            postsCollection.document(post.id)
                .set(postMap, SetOptions.merge())
                .await()

            // Update cache
            postDao.insertPost(post)

            Log.d(TAG, "Successfully pushed post: ${post.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push post: ${post.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a post from Firestore and cache
     * Uses batch delete for efficiency
     * @param postId Post ID to remove
     * @return Result indicating success or failure
     */
    suspend fun removePost(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val batch = firestore.batch()
            val postRef = postsCollection.document(postId)

            // Delete post
            batch.delete(postRef)

            // Commit batch
            batch.commit().await()

            // Remove from cache
            postDao.deletePostById(postId)

            Log.d(TAG, "Successfully removed post: $postId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove post: $postId", e)
            Result.failure(e)
        }
    }

    /**
     * Soft delete a post (mark as deleted without removing)
     * @param postId Post ID to soft delete
     * @return Result indicating success or failure
     */
    suspend fun softDeletePost(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mapOf(
                Fields.IS_DELETED to true,
                Fields.UPDATED_AT to System.currentTimeMillis()
            )

            postsCollection.document(postId)
                .update(updates)
                .await()

            postDao.softDeletePost(postId)

            Log.d(TAG, "Successfully soft deleted post: $postId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to soft delete post: $postId", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch paginated posts with their authors (20 per page)
     * Uses cache-first strategy with background refresh
     * @param page Page number (0-indexed)
     * @param forceRefresh Force fetch from Firestore even if cache exists
     * @return Result containing list of PostWithAuthor
     */
    suspend fun fetchPostsPage(
        page: Int,
        forceRefresh: Boolean = false
    ): Result<List<PostWithAuthor>> = withContext(Dispatchers.IO) {
        try {
            // Try cache first if not forcing refresh
            if (!forceRefresh && page == 0) {
                val cachedPosts = postDao.getPostsPaginated(PAGE_SIZE, 0)
                if (cachedPosts.isNotEmpty()) {
                    val postsWithAuthors = fetchAuthorsForPosts(cachedPosts)
                    if (postsWithAuthors.isNotEmpty()) {
                        Log.d(TAG, "Returning ${postsWithAuthors.size} posts from cache")
                        // Refresh in background
                        // Don't await this - let it update cache asynchronously
                        return@withContext Result.success(postsWithAuthors)
                    }
                }
            }

            // Reset pagination if requesting first page
            if (page == 0) {
                lastDocument = null
            }

            // Build Firestore query
            var query: Query = postsCollection
                .whereEqualTo(Fields.IS_DELETED, false)
                .whereEqualTo(Fields.IS_BANNED, false)
                .orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING)

            // Add pagination cursor if available
            lastDocument?.let {
                query = query.startAfter(it)
            }

            // Execute query with retry
            val querySnapshot = retryOperation {
                query.limit(PAGE_SIZE.toLong()).get().await()
            }

            // Update last document for next page
            if (querySnapshot.documents.isNotEmpty()) {
                lastDocument = querySnapshot.documents.last()
            }

            // Parse posts from Firestore
            val posts = querySnapshot.documents.mapNotNull { doc ->
                convertDocumentToPost(doc)
            }

            // Update cache
            if (posts.isNotEmpty()) {
                postDao.insertPosts(posts)
            }

            // Fetch authors for posts
            val postsWithAuthors = fetchAuthorsForPosts(posts)

            Log.d(TAG, "Fetched ${postsWithAuthors.size} posts from Firestore")
            Result.success(postsWithAuthors)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch posts page: $page", e)

            // Fallback to cache on error
            try {
                val cachedPosts = postDao.getPostsPaginated(PAGE_SIZE, page * PAGE_SIZE)
                if (cachedPosts.isNotEmpty()) {
                    val postsWithAuthors = fetchAuthorsForPosts(cachedPosts)
                    Log.d(TAG, "Returning ${postsWithAuthors.size} posts from cache (fallback)")
                    return@withContext Result.success(postsWithAuthors)
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Cache fallback also failed", cacheError)
            }

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
                // Try cache first
                val cachedPosts = postDao.getPostsByAuthor(authorId)
                if (cachedPosts.isNotEmpty()) {
                    val postsWithAuthors = fetchAuthorsForPosts(cachedPosts)
                    if (postsWithAuthors.isNotEmpty()) {
                        Log.d(TAG, "Returning ${postsWithAuthors.size} posts for author $authorId from cache")
                        // Continue to fetch from Firestore in background
                    }
                }

                // Fetch from Firestore
                val querySnapshot = retryOperation {
                    postsCollection
                        .whereEqualTo(Fields.AUTHOR_ID, authorId)
                        .whereEqualTo(Fields.IS_DELETED, false)
                        .whereEqualTo(Fields.IS_BANNED, false)
                        .orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING)
                        .get()
                        .await()
                }

                val posts = querySnapshot.documents.mapNotNull { doc ->
                    convertDocumentToPost(doc)
                }

                // Update cache
                if (posts.isNotEmpty()) {
                    postDao.insertPosts(posts)
                }

                // Fetch authors
                val postsWithAuthors = fetchAuthorsForPosts(posts)

                Log.d(TAG, "Fetched ${postsWithAuthors.size} posts for author: $authorId")
                Result.success(postsWithAuthors)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch posts for author: $authorId", e)

                // Fallback to cache
                try {
                    val cachedPosts = postDao.getPostsByAuthor(authorId)
                    val postsWithAuthors = fetchAuthorsForPosts(cachedPosts)
                    Log.d(TAG, "Returning ${postsWithAuthors.size} posts from cache (fallback)")
                    return@withContext Result.success(postsWithAuthors)
                } catch (cacheError: Exception) {
                    Log.e(TAG, "Cache fallback failed", cacheError)
                }

                Result.failure(e)
            }
        }

    /**
     * Fetch a single post with its author
     * @param postId Post ID to fetch
     * @return Result containing PostWithAuthor
     */
    suspend fun fetchPostWithAuthor(postId: String): Result<PostWithAuthor> = withContext(Dispatchers.IO) {
        try {
            // Try cache first
            val cachedPost = postDao.getPostById(postId)
            if (cachedPost != null && !cachedPost.isDeleted && !cachedPost.isBanned) {
                val authorResult = userExtractor.fetchCurrentUser(cachedPost.authorId)
                if (authorResult.isSuccess) {
                    val author = authorResult.getOrThrow()
                    Log.d(TAG, "Returning post $postId from cache")
                    // Continue to fetch from Firestore in background
                }
            }

            // Fetch from Firestore
            val document = retryOperation {
                postsCollection.document(postId).get().await()
            }

            if (!document.exists()) {
                return@withContext Result.failure(Exception("Post not found"))
            }

            val post = convertDocumentToPost(document)
                ?: return@withContext Result.failure(Exception("Failed to parse post"))

            // Check if post is deleted or banned
            if (post.isDeleted || post.isBanned) {
                return@withContext Result.failure(Exception("Post is not available"))
            }

            // Update cache
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

            Log.d(TAG, "Fetched post: $postId")
            Result.success(postWithAuthor)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch post: $postId", e)

            // Fallback to cache
            try {
                val cachedPost = postDao.getPostById(postId)
                if (cachedPost != null) {
                    val authorResult = userExtractor.fetchCurrentUser(cachedPost.authorId)
                    if (authorResult.isSuccess) {
                        val author = authorResult.getOrThrow()
                        val postWithAuthor = PostWithAuthor(cachedPost, author)
                        Log.d(TAG, "Returning post from cache (fallback)")
                        return@withContext Result.success(postWithAuthor)
                    }
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Cache fallback failed", cacheError)
            }

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
                .update(Fields.VIEWS_COUNT, FieldValue.increment(1))
                .await()

            // Update in cache
            postDao.incrementViewCount(postId)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to increment view count for post: $postId", e)
            // Still update cache even if Firestore fails
            try {
                postDao.incrementViewCount(postId)
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

    // ========== Private Helper Methods ==========

    /**
     * Convert Firestore document to Post
     */
    private fun convertDocumentToPost(doc: DocumentSnapshot): Post? {
        return try {
            val mediaUrls = doc.get(Fields.MEDIA_URLS) as? List<*>
            val hashtags = doc.get(Fields.HASHTAGS) as? List<*>
            val mentions = doc.get(Fields.MENTIONS) as? List<*>

            Post.create(
                id = doc.id,
                authorId = doc.getString(Fields.AUTHOR_ID) ?: "",
                content = doc.getString(Fields.CONTENT) ?: "",
                contentType = doc.getString(Fields.CONTENT_TYPE) ?: "text",
                mediaUrls = mediaUrls?.mapNotNull { it as? String } ?: emptyList(),
                createdAt = doc.getLong(Fields.CREATED_AT) ?: System.currentTimeMillis(),
                updatedAt = doc.getLong(Fields.UPDATED_AT) ?: System.currentTimeMillis(),
                visibility = doc.getString(Fields.VISIBILITY) ?: "public",
                location = doc.getString(Fields.LOCATION),
                commentsCount = doc.getLong(Fields.COMMENTS_COUNT)?.toInt() ?: 0,
                sharesCount = doc.getLong(Fields.SHARES_COUNT)?.toInt() ?: 0,
                viewsCount = doc.getLong(Fields.VIEWS_COUNT)?.toInt() ?: 0,
                isSponsored = doc.getBoolean(Fields.IS_SPONSORED) ?: false,
                isPinned = doc.getBoolean(Fields.IS_PINNED) ?: false,
                isDeleted = doc.getBoolean(Fields.IS_DELETED) ?: false,
                isBanned = doc.getBoolean(Fields.IS_BANNED) ?: false,
                status = doc.getString(Fields.STATUS) ?: "active",
                hashtags = hashtags?.mapNotNull { it as? String } ?: emptyList(),
                mentions = mentions?.mapNotNull { it as? String } ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert document to post: ${doc.id}", e)
            null
        }
    }

    /**
     * Build Firestore map from Post object
     */
    private fun buildPostMap(post: Post): Map<String, Any?> {
        return hashMapOf(
            Fields.ID to post.id,
            Fields.AUTHOR_ID to post.authorId,
            Fields.CONTENT to post.content,
            Fields.CONTENT_TYPE to post.contentType,
            Fields.MEDIA_URLS to post.mediaUrls,
            Fields.CREATED_AT to post.createdAt,
            Fields.UPDATED_AT to post.updatedAt,
            Fields.VISIBILITY to post.visibility,
            Fields.LOCATION to post.location,
            Fields.COMMENTS_COUNT to post.commentsCount,
            Fields.SHARES_COUNT to post.sharesCount,
            Fields.VIEWS_COUNT to post.viewsCount,
            Fields.IS_SPONSORED to post.isSponsored,
            Fields.IS_PINNED to post.isPinned,
            Fields.IS_DELETED to post.isDeleted,
            Fields.IS_BANNED to post.isBanned,
            Fields.STATUS to post.status,
            Fields.HASHTAGS to post.hashtags,
            Fields.MENTIONS to post.mentions
        )
    }

    /**
     * Fetch authors for a list of posts concurrently
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
                    authorId to result.getOrNull()
                }
            }.awaitAll().toMap()

            // Combine posts with their authors
            posts.mapNotNull { post ->
                val author = authorsMap[post.authorId]
                if (author != null) {
                    PostWithAuthor(post, author)
                } else {
                    Log.w(TAG, "Skipping post ${post.id} - author ${post.authorId} not found")
                    null
                }
            }
        }

    /**
     * Retry operation with exponential backoff
     */
    private suspend fun <T> retryOperation(
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    val delayMs = (1000L * (attempt + 1)) // Exponential backoff
                    Log.w(TAG, "Operation failed, retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxAttempts)", e)
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }

        throw lastException ?: Exception("Operation failed after $maxAttempts attempts")
    }

    /**
     * Data class for cache statistics
     */
    data class CacheStats(
        val cachedPostCount: Int
    )
}