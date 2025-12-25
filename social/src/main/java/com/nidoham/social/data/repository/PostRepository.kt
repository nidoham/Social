package com.nidoham.social.data.repository

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nidoham.social.data.local.AppDatabase
import com.nidoham.social.posts.Post
import com.nidoham.social.posts.PostWithAuthor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) {
    private val postDao = database.postDao()
    private val postsCollection = firestore.collection("posts")

    companion object {
        private const val TAG = "PostRepository"
        private const val PAGE_SIZE = 20
    }

    @OptIn(ExperimentalPagingApi::class)
    fun getPostsFeed(): Flow<PagingData<Post>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false, initialLoadSize = PAGE_SIZE),
            remoteMediator = PostRemoteMediator(database, firestore),
            pagingSourceFactory = { postDao.getPagedPosts() }
        ).flow
    }

    fun getUserPosts(authorId: String): Flow<PagingData<Post>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE),
            pagingSourceFactory = { postDao.getPagedPostsByAuthor(authorId) }
        ).flow
    }

    suspend fun createPost(post: Post): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            postsCollection.document(post.id).set(post).await()
            postDao.insertPost(post)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create post", e)
            Result.failure(e)
        }
    }

    suspend fun getPostWithAuthor(postId: String): Result<PostWithAuthor> = withContext(Dispatchers.IO) {
        try {
            val cachedPost = postDao.getPostById(postId)
            if (cachedPost != null && !cachedPost.isDeleted) {
                userRepository.getUserImmediately(cachedPost.authorId)?.let { author ->
                    return@withContext Result.success(PostWithAuthor(cachedPost, author))
                }
            }

            val snapshot = postsCollection.document(postId).get().await()
            val networkPost = snapshot.toObject(Post::class.java) ?: return@withContext Result.failure(Exception("Post not found"))

            if (networkPost.isDeleted) return@withContext Result.failure(Exception("Post is deleted"))

            postDao.insertPost(networkPost)
            val author = userRepository.getUserImmediately(networkPost.authorId) ?: return@withContext Result.failure(Exception("Author not found"))

            Result.success(PostWithAuthor(networkPost, author))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            postsCollection.document(postId).update("is_deleted", true).await()
            postDao.softDeletePost(postId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun likePost(postId: String) {
        try {
            postsCollection.document(postId).update("likes_count", FieldValue.increment(1))
            postDao.incrementLikeCount(postId)
        } catch (e: Exception) {
            Log.e(TAG, "Like failed", e)
        }
    }
}