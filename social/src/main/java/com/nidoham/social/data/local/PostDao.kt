package com.nidoham.social.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nidoham.social.posts.Post
import com.nidoham.social.posts.PostStatus
import com.nidoham.social.posts.PostVisibility
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    // ================= INSERT / UPDATE =================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: Post)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<Post>)

    @Update
    suspend fun updatePost(post: Post)

    // কন্টেন্ট আপডেটের জন্য কাস্টম কুয়েরি
    @Query("UPDATE posts SET content = :content, updatedAt = :updatedAt WHERE id = :postId")
    suspend fun updatePostContent(postId: String, content: String, updatedAt: Long = System.currentTimeMillis())

    // পিন স্ট্যাটাস টগল করার জন্য
    @Query("UPDATE posts SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :postId")
    suspend fun updatePinnedStatus(postId: String, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    // সফট ডিলিট (ফ্ল্যাগ true করা)
    @Query("UPDATE posts SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :postId")
    suspend fun softDeletePost(postId: String, updatedAt: Long = System.currentTimeMillis())


    // ================= READ & PAGINATION (FEED) =================

    @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: String): Post?

    @Query("SELECT * FROM posts WHERE id = :postId")
    fun getPostByIdFlow(postId: String): Flow<Post?>

    /**
     * Paging 3 Source for Infinite Scroll (News Feed).
     * এটি অটোমেটিক্যালি পেজিনেশন হ্যান্ডেল করবে।
     */
    @Query("""
        SELECT * FROM posts 
        WHERE isDeleted = 0 
        AND isBanned = 0 
        AND status = 'ACTIVE'
        ORDER BY isPinned DESC, createdAt DESC
    """)
    fun getPagedPosts(): PagingSource<Int, Post>

    // ইউজার প্রোফাইল ফিডের জন্য পেজিং
    @Query("""
        SELECT * FROM posts 
        WHERE authorId = :authorId 
        AND isDeleted = 0 
        AND isBanned = 0 
        ORDER BY isPinned DESC, createdAt DESC
    """)
    fun getPagedPostsByAuthor(authorId: String): PagingSource<Int, Post>

    // ম্যানুয়াল পেজিনেশন (যদি দরকার হয়)
    @Query("""
        SELECT * FROM posts 
        WHERE isDeleted = 0 
        AND isBanned = 0 
        ORDER BY createdAt DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPostsPaginated(limit: Int, offset: Int): List<Post>


    // ================= FILTERING =================

    @Query("SELECT * FROM posts WHERE isPinned = 1 AND isDeleted = 0")
    suspend fun getPinnedPosts(): List<Post>

    // Enum ব্যবহার করে ফিল্টারিং (String এর চেয়ে নিরাপদ)
    @Query("SELECT * FROM posts WHERE status = :status AND isDeleted = 0")
    suspend fun getPostsByStatus(status: PostStatus): List<Post>

    @Query("SELECT * FROM posts WHERE visibility = :visibility AND isDeleted = 0")
    suspend fun getPostsByVisibility(visibility: PostVisibility): List<Post>


    // ================= SEARCHING =================

    /**
     * Search inside content.
     * Note: Searching JSON arrays (hashtags) with LIKE is possible but not strictly accurate in SQLite.
     * For better search, consider using FTS4 module in Room.
     */
    @Query("""
        SELECT * FROM posts 
        WHERE content LIKE '%' || :query || '%' 
        AND isDeleted = 0 
        AND isBanned = 0 
        ORDER BY createdAt DESC 
        LIMIT :limit
    """)
    suspend fun searchPosts(query: String, limit: Int = 50): List<Post>


    // ================= COUNTERS & STATS =================

    @Query("SELECT COUNT(*) FROM posts WHERE isDeleted = 0 AND isBanned = 0")
    suspend fun getActivePostCount(): Int

    @Query("SELECT COUNT(*) FROM posts WHERE authorId = :authorId AND isDeleted = 0")
    suspend fun getPostCountByAuthor(authorId: String): Int


    // ================= ATOMIC INCREMENTS =================

    @Query("UPDATE posts SET viewsCount = viewsCount + 1 WHERE id = :postId")
    suspend fun incrementViewCount(postId: String)

    @Query("UPDATE posts SET likesCount = likesCount + 1 WHERE id = :postId")
    suspend fun incrementLikeCount(postId: String) // আগের মডেলে likesCount ছিল

    @Query("UPDATE posts SET commentsCount = commentsCount + :count WHERE id = :postId")
    suspend fun incrementCommentCount(postId: String, count: Int = 1)

    @Query("UPDATE posts SET sharesCount = sharesCount + :count WHERE id = :postId")
    suspend fun incrementShareCount(postId: String, count: Int = 1)


    // ================= DELETE / CLEANUP =================

    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deletePostById(postId: String)

    // ট্র‍্যাশ ক্লিনার: ৩০ দিনের পুরনো ডিলিটেড পোস্ট পার্মানেন্ট রিমুভ করার জন্য
    @Query("DELETE FROM posts WHERE isDeleted = 1 AND updatedAt < :beforeTimestamp")
    suspend fun deleteOldSoftDeletedPosts(beforeTimestamp: Long)

    @Query("DELETE FROM posts")
    suspend fun deleteAllPosts()
}
