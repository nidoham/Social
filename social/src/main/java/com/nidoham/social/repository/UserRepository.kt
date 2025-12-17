package com.nidoham.social.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.nidoham.social.model.User
import com.nidoham.social.model.UserMetadata
import com.nidoham.social.model.UserProfile
import com.nidoham.social.model.UserStats
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing user data across Firebase services.
 * Handles authentication, Firestore, and Realtime Database operations.
 */
class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val realtimeDb: DatabaseReference = FirebaseDatabase.getInstance().reference
) {

    // Collection references
    private val usersCollection = firestore.collection(COLLECTION_USERS)
    private val usersRealtimeRef = realtimeDb.child(COLLECTION_USERS)

    /**
     * Get the currently authenticated user's ID
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /**
     * Check if a user is currently logged in
     */
    fun isLoggedIn(): Boolean = auth.currentUser != null

    /**
     * Sign out the current user
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * Create a new user in both Firestore and Realtime Database
     * @param firebaseUser The authenticated Firebase user
     * @return Result indicating success or failure
     */
    suspend fun createUser(firebaseUser: FirebaseUser): Result<String> {
        return try {
            val userId = firebaseUser.uid
            val user = buildUserFromFirebaseUser(firebaseUser, userId)

            // Save to Firestore
            usersCollection.document(userId)
                .set(user)
                .await()

            // Save to Realtime Database
            usersRealtimeRef.child(userId)
                .setValue(user)
                .await()

            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user by ID from Firestore
     */
    suspend fun getUserById(userId: String): Result<User?> {
        return try {
            val document = usersCollection.document(userId).get().await()
            val user = document.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user profile
     */
    suspend fun updateUser(userId: String, user: User): Result<Unit> {
        return try {
            val updatedUser = user.copy(
                metadata = user.metadata.copy(
                    updatedAt = System.currentTimeMillis()
                )
            )

            usersCollection.document(userId)
                .set(updatedUser)
                .await()

            usersRealtimeRef.child(userId)
                .setValue(updatedUser)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update last login timestamp
     */
    suspend fun updateLastLogin(userId: String): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()
            val updates = mapOf(
                "metadata.lastLoginAt" to timestamp,
                "metadata.updatedAt" to timestamp
            )

            usersCollection.document(userId)
                .update(updates)
                .await()

            usersRealtimeRef.child(userId)
                .child("metadata")
                .updateChildren(mapOf(
                    "lastLoginAt" to timestamp,
                    "updatedAt" to timestamp
                ))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Build User object from FirebaseUser
     */
    private fun buildUserFromFirebaseUser(
        firebaseUser: FirebaseUser,
        userId: String
    ): User {
        val currentTime = System.currentTimeMillis()
        val displayName = firebaseUser.displayName ?: ""
        val email = firebaseUser.email ?: ""
        val photoUrl = firebaseUser.photoUrl?.toString() ?: ""

        return User(
            id = userId,
            profile = UserProfile(
                username = generateUsername(displayName, email),
                displayName = displayName,
                email = email,
                avatarUrl = photoUrl,
                coverUrl = null,
                bio = null,
                website = null,
                gender = null,
                pronouns = null,
                birthDate = 0L // Should be set during onboarding
            ),
            stats = UserStats(
                postCount = 0,
                followerCount = 0,
                followingCount = 0
            ),
            metadata = UserMetadata(
                createdAt = currentTime,
                updatedAt = currentTime,
                lastLoginAt = currentTime,
                lastLogoutAt = 0L,
                isVerified = false
            )
        )
    }

    /**
     * Generate a unique username from display name or email
     */
    private fun generateUsername(displayName: String, email: String): String {
        return when {
            displayName.isNotBlank() -> displayName
                .lowercase()
                .replace(WHITESPACE_REGEX, "")
                .take(MAX_USERNAME_LENGTH)
            email.isNotBlank() -> email
                .substringBefore("@")
                .lowercase()
                .take(MAX_USERNAME_LENGTH)
            else -> "user${System.currentTimeMillis()}"
        }
    }

    companion object {
        private const val COLLECTION_USERS = "users"
        private const val MAX_USERNAME_LENGTH = 30
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}