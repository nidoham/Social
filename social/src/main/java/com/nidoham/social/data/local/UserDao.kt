package com.nidoham.social.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nidoham.social.user.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    // ================= INSERT / UPDATE =================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    // ================= READ (ONE-SHOT & REACTIVE) =================

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserByIdFlow(userId: String): Flow<User?>

    // ================= SEARCH (NEW) =================

    /**
     * Search users by username, firstName, or lastName.
     * Uses SQL 'LIKE' for partial matching (case-insensitive in SQLite usually).
     */
    @Query("""
        SELECT * FROM users 
        WHERE username LIKE '%' || :query || '%' 
        OR firstName LIKE '%' || :query || '%' 
        OR lastName LIKE '%' || :query || '%'
    """)
    suspend fun searchUsers(query: String): List<User>

    // ================= PAGINATION =================

    @Query("SELECT * FROM users ORDER BY onlineAtMillis DESC")
    fun getUsersPagingSource(): PagingSource<Int, User>

    @Query("SELECT * FROM users ORDER BY onlineAtMillis DESC LIMIT :limit OFFSET :offset")
    suspend fun getUsersPaginated(limit: Int, offset: Int): List<User>

    // ================= UTILS & DELETE =================

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("UPDATE users SET onlineAtMillis = :timestamp WHERE id = :userId")
    suspend fun updateAccessTime(userId: String, timestamp: Long)

    // ================= CACHE MANAGEMENT =================

    @Query("""
        DELETE FROM users 
        WHERE id NOT IN (
            SELECT id FROM users 
            ORDER BY onlineAtMillis DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun deleteUsersNotInTop(keepCount: Int)
}
