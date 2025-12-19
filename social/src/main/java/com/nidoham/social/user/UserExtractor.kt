package com.nidoham.social.user

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * UserExtractor handles user data operations with Firestore and Room caching
 * Implements a cache-first strategy with a limit of 100 recent users
 */
class UserExtractor(context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")
    private val database = UserDatabase.getInstance(context)
    private val userDao = database.userDao()

    companion object {
        private const val CACHE_LIMIT = 100
        private const val PAGE_SIZE = 20
        private const val USERS_COLLECTION = "users"
    }

    /**
     * Push a user to Firestore and update cache
     * @param user User to push
     * @return Result indicating success or failure
     */
    suspend fun pushUser(user: User): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Push to Firestore
            usersCollection.document(user.id)
                .set(user.toMap())
                .await()

            // Update cache
            userDao.insertUser(user)
            maintainCacheLimit()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a user from Firestore and cache
     * @param userId User ID to remove
     * @return Result indicating success or failure
     */
    suspend fun removeUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Remove from Firestore
            usersCollection.document(userId)
                .delete()
                .await()

            // Remove from cache
            userDao.deleteUserById(userId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch current user only (single user by ID)
     * Checks cache first, then Firestore if not found
     * @param userId User ID to fetch
     * @return Result containing User or error
     */
    suspend fun fetchCurrentUser(userId: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            var user = userDao.getUserById(userId)

            if (user != null) {
                // Update access time for LRU
                userDao.updateAccessTime(userId, System.currentTimeMillis())
                return@withContext Result.success(user)
            }

            // Not in cache, fetch from Firestore
            val document = usersCollection.document(userId).get().await()

            if (document.exists()) {
                user = User.fromMap(document.data ?: emptyMap())

                // Add to cache
                userDao.insertUser(user)
                maintainCacheLimit()

                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch paginated users (20 per page)
     * Checks cache first, then Firestore if not sufficient
     * @param page Page number (0-indexed)
     * @return Result containing list of users
     */
    suspend fun fetchUsersPage(page: Int): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            val offset = page * PAGE_SIZE

            // Try to get from cache first
            val cachedUsers = userDao.getUsersPaginated(PAGE_SIZE, offset)

            // If we have enough users in cache, return them
            if (cachedUsers.size == PAGE_SIZE) {
                return@withContext Result.success(cachedUsers)
            }

            // Otherwise, fetch from Firestore
            val querySnapshot = usersCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())
                .get()
                .await()

            val users = parseUsersFromSnapshot(querySnapshot)

            // Update cache with fetched users
            if (users.isNotEmpty()) {
                userDao.insertUsers(users)
                maintainCacheLimit()
            }

            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch users page with custom query starting after a document
     * Useful for pagination with Firestore cursors
     * @param page Page number
     * @param lastUserId Last user ID from previous page (for cursor)
     * @return Result containing list of users
     */
    suspend fun fetchUsersPageAfter(page: Int, lastUserId: String? = null): Result<List<User>> =
        withContext(Dispatchers.IO) {
            try {
                var query = usersCollection
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE.toLong())

                // If we have a lastUserId, use it as cursor
                if (lastUserId != null) {
                    val lastDoc = usersCollection.document(lastUserId).get().await()
                    if (lastDoc.exists()) {
                        query = query.startAfter(lastDoc)
                    }
                }

                val querySnapshot = query.get().await()
                val users = parseUsersFromSnapshot(querySnapshot)

                // Update cache
                if (users.isNotEmpty()) {
                    userDao.insertUsers(users)
                    maintainCacheLimit()
                }

                Result.success(users)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Clear all cached users
     */
    suspend fun clearCache(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.deleteAllUsers()
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
            cachedUserCount = userDao.getUserCount(),
            cacheLimit = CACHE_LIMIT
        )
    }

    /**
     * Maintain cache limit by removing oldest users if exceeds limit
     */
    private suspend fun maintainCacheLimit() {
        val currentCount = userDao.getUserCount()
        if (currentCount > CACHE_LIMIT) {
            val toDelete = currentCount - CACHE_LIMIT
            userDao.deleteOldestUsers(toDelete)
        }
    }

    /**
     * Parse users from Firestore QuerySnapshot
     */
    private fun parseUsersFromSnapshot(snapshot: QuerySnapshot): List<User> {
        return snapshot.documents.mapNotNull { document ->
            try {
                User.fromMap(document.data ?: emptyMap())
            } catch (e: Exception) {
                null // Skip invalid documents
            }
        }
    }

    /**
     * Data class for cache statistics
     */
    data class CacheStats(
        val cachedUserCount: Int,
        val cacheLimit: Int
    ) {
        val cacheUsagePercentage: Float
            get() = (cachedUserCount.toFloat() / cacheLimit) * 100
    }
}