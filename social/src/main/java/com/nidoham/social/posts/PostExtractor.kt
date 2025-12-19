package com.nidoham.social.posts

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
        private const val PAGE_SIZE = 20
    }

    /**
     * Push a post to Firestore and update cache
     * @param post Post to push
     * @return Result indicating success or failure
     */
    suspend fun pushPost(post: Post): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Convert post to map for Firestore
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
                "reactions" to hashMapOf(
                    "likes" to post.reactions.likes,
                    "loves" to post.reactions.loves,
                    "wows" to post.reactions.wows,
                ),
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

            // Push to Firestore
            postsCollection.document(post.id)
                .set(postMap)
                .await()

            // Update cache
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
            // Remove from Firestore
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
     * Cache-first strategy: checks cache first, then Firestore if not sufficient
     * @param page Page number (0-indexed)
     * @return Result containing list of PostWithAuthor
     */
    suspend fun fetchPostsPage(page: Int): Result<List<PostWithAuthor>> = withContext(Dispatchers.IO) {
        try {
            val offset = page * PAGE_SIZE

            // Try to get from cache first
            val cachedPosts = postDao.getPostsPaginated(PAGE_SIZE, offset)

            // If we have enough posts in cache, use them
            if (cachedPosts.size == PAGE_SIZE) {
                val postsWithAuthors = fetchAuthorsForPosts(cachedPosts)
                return@withContext Result.success(postsWithAuthors)
            }

            // Otherwise, fetch from Firestore
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

            // Parse posts from Firestore
            val posts = querySnapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Post::class.java)
                } catch (e: Exception) {
                    null
                }
            }

            // Update cache
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
                // Try cache first
                var posts = postDao.getPostsByAuthor(authorId)

                // If not in cache, fetch from Firestore
                if (posts.isEmpty()) {
                    val querySnapshot = postsCollection
                        .whereEqualTo("authorId", authorId)
                        .whereEqualTo("isDeleted", false)
                        .whereEqualTo("isBanned", false)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .get()
                        .await()

                    posts = querySnapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Post::class.java)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    // Update cache
                    if (posts.isNotEmpty()) {
                        postDao.insertPosts(posts)
                    }
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
                // Check cache first
                var post = postDao.getPostById(postId)

                // If not in cache, fetch from Firestore
                if (post == null) {
                    val document = postsCollection.document(postId).get().await()

                    if (document.exists()) {
                        post = document.toObject(Post::class.java)
                        if (post != null) {
                            postDao.insertPost(post)
                        }
                    } else {
                        return@withContext Result.failure(Exception("Post not found"))
                    }
                }

                // Check if post is deleted or banned
                if (post!!.isDeleted || post.isBanned) {
                    return@withContext Result.failure(Exception("Post is not available"))
                }

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