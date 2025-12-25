package com.nidoham.social.data.repository

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.google.firebase.firestore.FirebaseFirestore
import com.nidoham.social.data.local.AppDatabase
import com.nidoham.social.data.local.isActive
import com.nidoham.social.stories.Story
import com.nidoham.social.stories.StoryWithAuthor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryRepository @Inject constructor(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) {
    private val storyDao = database.storyDao()
    private val storiesCollection = firestore.collection("stories")

    companion object {
        private const val TAG = "StoryRepository"
        private const val PAGE_SIZE = 20
    }

    @OptIn(ExperimentalPagingApi::class)
    fun getStoriesStream(): Flow<PagingData<Story>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = StoryRemoteMediator(database, firestore),
            pagingSourceFactory = { storyDao.getActiveStoriesPagingSource() }
        ).flow
    }

    suspend fun createStory(story: Story): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            storiesCollection.document(story.id).set(story).await()
            storyDao.insertStory(story)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create story", e)
            Result.failure(e)
        }
    }

    suspend fun getStoryWithAuthor(storyId: String): Result<StoryWithAuthor> = withContext(Dispatchers.IO) {
        try {
            val cachedStory = storyDao.getStoryById(storyId)
            if (cachedStory != null && cachedStory.isActive()) {
                userRepository.getUserImmediately(cachedStory.authorId)?.let { author ->
                    return@withContext Result.success(StoryWithAuthor(cachedStory, author))
                }
            }

            val snapshot = storiesCollection.document(storyId).get().await()
            val story = snapshot.toObject(Story::class.java) ?: return@withContext Result.failure(Exception("Story not found"))

            if (!story.isActive()) return@withContext Result.failure(Exception("Story expired"))

            storyDao.insertStory(story)
            val author = userRepository.getUserImmediately(story.authorId) ?: return@withContext Result.failure(Exception("Author not found"))

            Result.success(StoryWithAuthor(story, author))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            storiesCollection.document(storyId).delete().await()
            storyDao.deleteStoryById(storyId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun incrementViewCount(storyId: String) {
        try {
            storiesCollection.document(storyId).update("views_count", com.google.firebase.firestore.FieldValue.increment(1))
            storyDao.incrementViewCount(storyId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to increment view", e)
        }
    }
}