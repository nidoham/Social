package com.nidoham.social.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.nidoham.social.data.local.UserDao
import com.nidoham.social.user.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore
) {
    private val usersCollection = firestore.collection(COLLECTION_USERS)

    companion object {
        private const val TAG = "UserRepository"
        private const val COLLECTION_USERS = "users"
        private const val CACHE_LIMIT = 100
    }

    sealed class Resource<out T> {
        data class Success<T>(val data: T) : Resource<T>()
        data class Error(val exception: Throwable, val message: String? = null) : Resource<Nothing>()
        data object Loading : Resource<Nothing>()
    }

    fun getUser(userId: String): Flow<Resource<User>> = flow {
        emit(Resource.Loading)

        val cachedUser = userDao.getUserById(userId)
        if (cachedUser != null) {
            emit(Resource.Success(cachedUser))
        }

        try {
            val document = usersCollection.document(userId).get().await()
            if (document.exists()) {
                val networkUser = document.toObject(User::class.java)
                if (networkUser != null) {
                    userDao.insertUser(networkUser)
                    emit(Resource.Success(networkUser))
                }
            } else if (cachedUser == null) {
                emit(Resource.Error(Exception("User not found")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch error: ${e.message}")
            if (cachedUser == null) emit(Resource.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveUser(user: User): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            usersCollection.document(user.id).set(user).await()
            userDao.insertUser(user)
            userDao.deleteUsersNotInTop(CACHE_LIMIT)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Save user failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserImmediately(userId: String): User? = withContext(Dispatchers.IO) {
        val cached = userDao.getUserById(userId)
        if (cached != null) return@withContext cached

        try {
            val snapshot = usersCollection.document(userId).get().await()
            snapshot.toObject(User::class.java)?.also { userDao.insertUser(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            usersCollection.document(userId).delete().await()
            userDao.deleteUserById(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String): List<User> = withContext(Dispatchers.IO) {
        val localResults = userDao.searchUsers(query)
        if (localResults.isNotEmpty()) return@withContext localResults

        try {
            val snapshot = usersCollection
                .whereGreaterThanOrEqualTo("username", query)
                .whereLessThanOrEqualTo("username", query + "\uf8ff")
                .limit(20)
                .get()
                .await()

            val networkUsers = snapshot.toObjects(User::class.java)
            userDao.insertUsers(networkUsers)
            networkUsers
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            emptyList()
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        userDao.deleteAllUsers()
    }
}