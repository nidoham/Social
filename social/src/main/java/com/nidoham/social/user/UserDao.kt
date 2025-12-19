package com.nidoham.social.user

import androidx.room.*

/**
 * Data Access Object for User entities in Room Database
 * Handles all database operations for user caching
 */
@Dao
interface UserDao {

    /**
     * Insert or update a user in the cache
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    /**
     * Insert or update multiple users in the cache
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    /**
     * Get a user by ID from cache
     */
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    /**
     * Get paginated users ordered by most recently accessed
     */
    @Query("SELECT * FROM users ORDER BY onlineAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getUsersPaginated(limit: Int, offset: Int): List<User>

    /**
     * Get all users ordered by most recent access
     */
    @Query("SELECT * FROM users ORDER BY onlineAt DESC")
    suspend fun getAllUsers(): List<User>

    /**
     * Get total count of cached users
     */
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    /**
     * Delete a user by ID from cache
     */
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)

    /**
     * Delete oldest users to maintain cache limit
     * Keeps only the most recent N users
     */
    @Query("""
        DELETE FROM users WHERE id IN (
            SELECT id FROM users 
            ORDER BY onlineAt ASC 
            LIMIT :count
        )
    """)
    suspend fun deleteOldestUsers(count: Int)

    /**
     * Delete all users from cache
     */
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    /**
     * Update user's online timestamp (for cache LRU)
     */
    @Query("UPDATE users SET onlineAt = :timestamp WHERE id = :userId")
    suspend fun updateAccessTime(userId: String, timestamp: Long)
}