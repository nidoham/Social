package com.nidoham.social.posts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for Post operations
 */
@Dao
interface PostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: Post)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<Post>)

    @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: String): Post?

    @Query("SELECT * FROM posts WHERE isDeleted = 0 AND isBanned = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPostsPaginated(limit: Int, offset: Int): List<Post>

    @Query("SELECT * FROM posts WHERE authorId = :authorId AND isDeleted = 0 AND isBanned = 0 ORDER BY createdAt DESC")
    suspend fun getPostsByAuthor(authorId: String): List<Post>

    @Query("SELECT COUNT(*) FROM posts WHERE isDeleted = 0 AND isBanned = 0")
    suspend fun getActivePostCount(): Int

    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deletePostById(postId: String)

    @Query("DELETE FROM posts")
    suspend fun deleteAllPosts()

    @Query("UPDATE posts SET viewsCount = viewsCount + 1 WHERE id = :postId")
    suspend fun incrementViewCount(postId: String)
}