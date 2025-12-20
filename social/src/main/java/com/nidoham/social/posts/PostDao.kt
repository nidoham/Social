package com.nidoham.social.posts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for Post operations
 */
@Dao
interface PostDao {
    // Insert operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: Post)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<Post>)

    // Update operations
    @Update
    suspend fun updatePost(post: Post)

    @Query("UPDATE posts SET content = :content, updatedAt = :updatedAt WHERE id = :postId")
    suspend fun updatePostContent(postId: String, content: String, updatedAt: Long)

    @Query("UPDATE posts SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :postId")
    suspend fun softDeletePost(postId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE posts SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :postId")
    suspend fun updatePinnedStatus(postId: String, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    // Get operations
    @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: String): Post?

    @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
    fun getPostByIdFlow(postId: String): Flow<Post?>

    @Query("SELECT * FROM posts WHERE isDeleted = 0 AND isBanned = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPostsPaginated(limit: Int, offset: Int): List<Post>

    @Query("SELECT * FROM posts WHERE isDeleted = 0 AND isBanned = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun getPostsPaginatedFlow(limit: Int, offset: Int): Flow<List<Post>>

    @Query("SELECT * FROM posts WHERE authorId = :authorId AND isDeleted = 0 AND isBanned = 0 ORDER BY createdAt DESC")
    suspend fun getPostsByAuthor(authorId: String): List<Post>

    @Query("SELECT * FROM posts WHERE authorId = :authorId AND isDeleted = 0 AND isBanned = 0 ORDER BY createdAt DESC")
    fun getPostsByAuthorFlow(authorId: String): Flow<List<Post>>

    @Query("SELECT * FROM posts WHERE isPinned = 1 AND isDeleted = 0 AND isBanned = 0 ORDER BY createdAt DESC")
    suspend fun getPinnedPosts(): List<Post>

    @Query("SELECT * FROM posts WHERE isSponsored = 1 AND isDeleted = 0 AND isBanned = 0 ORDER BY createdAt DESC")
    suspend fun getSponsoredPosts(): List<Post>

    @Query("SELECT * FROM posts WHERE status = :status AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getPostsByStatus(status: String): List<Post>

    // Search operations
    @Query("""
        SELECT * FROM posts 
        WHERE (content LIKE '%' || :searchQuery || '%' 
            OR hashtagsString LIKE '%' || :searchQuery || '%' 
            OR mentionsString LIKE '%' || :searchQuery || '%')
        AND isDeleted = 0 AND isBanned = 0 
        ORDER BY createdAt DESC 
        LIMIT :limit
    """)
    suspend fun searchPosts(searchQuery: String, limit: Int = 50): List<Post>

    @Query("""
        SELECT * FROM posts 
        WHERE hashtagsString LIKE '%' || :hashtag || '%'
        AND isDeleted = 0 AND isBanned = 0 
        ORDER BY createdAt DESC
    """)
    suspend fun getPostsByHashtag(hashtag: String): List<Post>

    // Count operations
    @Query("SELECT COUNT(*) FROM posts WHERE isDeleted = 0 AND isBanned = 0")
    suspend fun getActivePostCount(): Int

    @Query("SELECT COUNT(*) FROM posts WHERE authorId = :authorId AND isDeleted = 0 AND isBanned = 0")
    suspend fun getPostCountByAuthor(authorId: String): Int

    // Increment operations
    @Query("UPDATE posts SET viewsCount = viewsCount + 1 WHERE id = :postId")
    suspend fun incrementViewCount(postId: String)

    @Query("UPDATE posts SET commentsCount = commentsCount + :count WHERE id = :postId")
    suspend fun incrementCommentCount(postId: String, count: Int = 1)

    @Query("UPDATE posts SET sharesCount = sharesCount + :count WHERE id = :postId")
    suspend fun incrementShareCount(postId: String, count: Int = 1)

    // Delete operations
    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deletePostById(postId: String)

    @Query("DELETE FROM posts WHERE isDeleted = 1 AND updatedAt < :beforeTimestamp")
    suspend fun deleteOldSoftDeletedPosts(beforeTimestamp: Long)

    @Query("DELETE FROM posts")
    suspend fun deleteAllPosts()
}
