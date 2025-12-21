package com.nidoham.social.user

import android.content.Context
import android.util.Log
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
        private const val TAG = "UserExtractor"
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

            Log.d(TAG, "Successfully pushed user: ${user.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push user: ${user.id}", e)
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

            Log.d(TAG, "Successfully removed user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove user: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch current user only (single user by ID)
     * @param userId User ID to fetch
     * @param force If true, skip cache and fetch directly from Firestore
     * @return Result containing User or error
     */
    suspend fun fetchCurrentUser(
        userId: String,
        force: Boolean = false
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            // Try cache first if not forcing refresh
            if (!force) {
                val cachedUser = userDao.getUserById(userId)
                if (cachedUser != null) {
                    // Update access time for LRU
                    userDao.updateAccessTime(userId, System.currentTimeMillis())
                    Log.d(TAG, "Returning user $userId from cache")
                    return@withContext Result.success(cachedUser)
                }
            }

            // Fetch from Firestore
            val document = usersCollection.document(userId).get().await()

            if (!document.exists()) {
                return@withContext Result.failure(Exception("User not found"))
            }

            val user = User.fromMap(document.data ?: emptyMap())

            // Add to cache
            userDao.insertUser(user)
            maintainCacheLimit()

            Log.d(TAG, "Fetched user: $userId from Firestore")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user: $userId", e)

            // Fallback to cache on error
            if (force) {
                try {
                    val cachedUser = userDao.getUserById(userId)
                    if (cachedUser != null) {
                        Log.d(TAG, "Returning user from cache (fallback)")
                        return@withContext Result.success(cachedUser)
                    }
                } catch (cacheError: Exception) {
                    Log.e(TAG, "Cache fallback failed", cacheError)
                }
            }

            Result.failure(e)
        }
    }

    /**
     * Fetch paginated users (20 per page)
     * @param page Page number (0-indexed)
     * @param force If true, skip cache and fetch directly from Firestore
     * @return Result containing list of users
     */
    suspend fun fetchUsersPage(
        page: Int,
        force: Boolean = false
    ): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            val offset = page * PAGE_SIZE

            // Try cache first if not forcing refresh
            if (!force) {
                val cachedUsers = userDao.getUsersPaginated(PAGE_SIZE, offset)
                // If we have enough users in cache, return them
                if (cachedUsers.size == PAGE_SIZE || (page > 0 && cachedUsers.isNotEmpty())) {
                    Log.d(TAG, "Returning ${cachedUsers.size} users from cache")
                    return@withContext Result.success(cachedUsers)
                }
            }

            // Fetch from Firestore
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

            Log.d(TAG, "Fetched ${users.size} users from Firestore")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch users page: $page", e)

            // Fallback to cache on error
            try {
                val offset = page * PAGE_SIZE
                val cachedUsers = userDao.getUsersPaginated(PAGE_SIZE, offset)
                if (cachedUsers.isNotEmpty()) {
                    Log.d(TAG, "Returning ${cachedUsers.size} users from cache (fallback)")
                    return@withContext Result.success(cachedUsers)
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Cache fallback failed", cacheError)
            }

            Result.failure(e)
        }
    }

    /**
     * Fetch users page with custom query starting after a document
     * Useful for pagination with Firestore cursors
     * @param page Page number
     * @param lastUserId Last user ID from previous page (for cursor)
     * @param force If true, skip cache and fetch directly from Firestore
     * @return Result containing list of users
     */
    suspend fun fetchUsersPageAfter(
        page: Int,
        lastUserId: String? = null,
        force: Boolean = false
    ): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            // Try cache first if not forcing refresh and no cursor
            if (!force && lastUserId == null) {
                val offset = page * PAGE_SIZE
                val cachedUsers = userDao.getUsersPaginated(PAGE_SIZE, offset)
                if (cachedUsers.size == PAGE_SIZE || (page > 0 && cachedUsers.isNotEmpty())) {
                    Log.d(TAG, "Returning ${cachedUsers.size} users from cache")
                    return@withContext Result.success(cachedUsers)
                }
            }

            // Build Firestore query
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

            Log.d(TAG, "Fetched ${users.size} users from Firestore (with cursor)")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch users page after: $lastUserId", e)

            // Fallback to cache on error
            try {
                val offset = page * PAGE_SIZE
                val cachedUsers = userDao.getUsersPaginated(PAGE_SIZE, offset)
                if (cachedUsers.isNotEmpty()) {
                    Log.d(TAG, "Returning ${cachedUsers.size} users from cache (fallback)")
                    return@withContext Result.success(cachedUsers)
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Cache fallback failed", cacheError)
            }

            Result.failure(e)
        }
    }

    /**
     * Clear all cached users
     */
    suspend fun clearCache(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.deleteAllUsers()
            Log.d(TAG, "Cleared cache")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
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
        try {
            val currentCount = userDao.getUserCount()
            if (currentCount > CACHE_LIMIT) {
                val toDelete = currentCount - CACHE_LIMIT
                userDao.deleteOldestUsers(toDelete)
                Log.d(TAG, "Maintained cache limit: deleted $toDelete old users")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to maintain cache limit", e)
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
                Log.e(TAG, "Failed to parse user: ${document.id}", e)
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