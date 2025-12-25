package com.nidoham.social.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nidoham.social.data.local.AppDatabase
import com.nidoham.social.posts.Post
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalPagingApi::class)
class PostRemoteMediator(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore
) : RemoteMediator<Int, Post>() {

    private val postDao = database.postDao()

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Post>
    ): MediatorResult {
        return try {
            val loadKey = when (loadType) {
                LoadType.REFRESH -> null
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)
                    lastItem.createdAt
                }
            }

            var query = firestore.collection("posts")
                .whereEqualTo("is_deleted", false)
                .whereEqualTo("is_banned", false)
                .whereEqualTo("status", "ACTIVE")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(state.config.pageSize.toLong())

            if (loadKey != null) {
                query = query.startAfter(loadKey)
            }

            val snapshot = query.get().await()
            val posts = snapshot.toObjects(Post::class.java)

            database.withTransaction {
                postDao.insertPosts(posts)
            }

            MediatorResult.Success(endOfPaginationReached = posts.isEmpty())
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}