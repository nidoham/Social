package com.nidoham.social.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nidoham.social.data.local.AppDatabase
import com.nidoham.social.stories.Story
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalPagingApi::class)
class StoryRemoteMediator(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore
) : RemoteMediator<Int, Story>() {

    private val storyDao = database.storyDao()

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Story>
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

            var query = firestore.collection("stories")
                .whereEqualTo("is_deleted", false)
                .whereGreaterThan("expires_at", System.currentTimeMillis())
                .orderBy("expires_at")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(state.config.pageSize.toLong())

            if (loadKey != null) {
                query = query.startAfter(loadKey)
            }

            val snapshot = query.get().await()
            val stories = snapshot.toObjects(Story::class.java)

            database.withTransaction {
                if (loadType == LoadType.REFRESH) storyDao.deleteAllStories()
                storyDao.insertStories(stories)
            }

            MediatorResult.Success(endOfPaginationReached = stories.size < state.config.pageSize)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}