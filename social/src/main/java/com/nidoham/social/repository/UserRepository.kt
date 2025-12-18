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
import java.util.Date

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
            // Update the metadata with current timestamp
            val updatedUser = user.copy(
                metadata = user.metadata.markAsUpdated()
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
    suspend fun updateLastLogin(
        userId: String,
        deviceId: String? = null,
        ipAddress: String? = null
    ): Result<Unit> {
        return try {
            // Get the current user
            val userResult = getUserById(userId)
            if (userResult.isFailure) {
                return Result.failure(userResult.exceptionOrNull() ?: Exception("User not found"))
            }

            val user = userResult.getOrNull() ?: return Result.failure(Exception("User not found"))

            // Update using the model's method
            val updatedUser = user.login(deviceId, ipAddress)

            // Save to Firestore
            usersCollection.document(userId)
                .set(updatedUser)
                .await()

            // Save to Realtime Database
            usersRealtimeRef.child(userId)
                .setValue(updatedUser)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user profile information
     */
    suspend fun updateUserProfile(
        userId: String,
        displayName: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
        coverUrl: String? = null,
        website: String? = null,
        location: String? = null,
        pronouns: String? = null,
        gender: String? = null
    ): Result<Unit> {
        return try {
            val userResult = getUserById(userId)
            if (userResult.isFailure) {
                return Result.failure(userResult.exceptionOrNull() ?: Exception("User not found"))
            }

            val user = userResult.getOrNull() ?: return Result.failure(Exception("User not found"))

            val updatedUser = user.updateProfile(
                displayName = displayName,
                bio = bio,
                avatarUrl = avatarUrl,
                coverUrl = coverUrl,
                website = website,
                location = location,
                pronouns = pronouns,
                gender = gender
            )

            return updateUser(userId, updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Follow a user
     */
    suspend fun followUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            // Update current user's following count
            val currentUserResult = getUserById(currentUserId)
            if (currentUserResult.isFailure) {
                return Result.failure(currentUserResult.exceptionOrNull() ?: Exception("Current user not found"))
            }
            val currentUser = currentUserResult.getOrNull() ?: return Result.failure(Exception("Current user not found"))
            updateUser(currentUserId, currentUser.follow())

            // Update target user's follower count
            val targetUserResult = getUserById(targetUserId)
            if (targetUserResult.isFailure) {
                return Result.failure(targetUserResult.exceptionOrNull() ?: Exception("Target user not found"))
            }
            val targetUser = targetUserResult.getOrNull() ?: return Result.failure(Exception("Target user not found"))
            updateUser(targetUserId, targetUser.gainFollower())

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unfollow a user
     */
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            // Update current user's following count
            val currentUserResult = getUserById(currentUserId)
            if (currentUserResult.isFailure) {
                return Result.failure(currentUserResult.exceptionOrNull() ?: Exception("Current user not found"))
            }
            val currentUser = currentUserResult.getOrNull() ?: return Result.failure(Exception("Current user not found"))
            updateUser(currentUserId, currentUser.unfollow())

            // Update target user's follower count
            val targetUserResult = getUserById(targetUserId)
            if (targetUserResult.isFailure) {
                return Result.failure(targetUserResult.exceptionOrNull() ?: Exception("Target user not found"))
            }
            val targetUser = targetUserResult.getOrNull() ?: return Result.failure(Exception("Target user not found"))
            updateUser(targetUserId, targetUser.loseFollower())

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search users by username
     */
    suspend fun searchUsersByUsername(query: String, limit: Int = 20): Result<List<User>> {
        return try {
            val normalizedQuery = query.lowercase().trim()

            val documents = usersCollection
                .whereGreaterThanOrEqualTo("profile.username", normalizedQuery)
                .whereLessThanOrEqualTo("profile.username", normalizedQuery + '\uf8ff')
                .limit(limit.toLong())
                .get()
                .await()

            val users = documents.mapNotNull { it.toObject(User::class.java) }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if username is available
     */
    suspend fun isUsernameAvailable(username: String): Result<Boolean> {
        return try {
            val normalizedUsername = username.lowercase().trim()

            val documents = usersCollection
                .whereEqualTo("profile.username", normalizedUsername)
                .limit(1)
                .get()
                .await()

            Result.success(documents.isEmpty)
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
        val currentTime = Date()
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
                birthDate = null, // Should be set during onboarding
                location = null,
                phoneNumber = null
            ),
            stats = UserStats(
                postCount = 0L,
                followerCount = 0L,
                followingCount = 0L,
                storyCount = 0L,
                likeCount = 0L,
                commentCount = 0L
            ),
            metadata = UserMetadata(
                createdAt = currentTime,
                updatedAt = currentTime,
                lastLoginAt = currentTime,
                lastLogoutAt = null,
                isVerified = firebaseUser.isEmailVerified,
                isActive = true,
                isBanned = false,
                isPrivate = false,
                suspensionReason = null,
                suspendedUntil = null,
                emailVerifiedAt = if (firebaseUser.isEmailVerified) currentTime else null,
                phoneVerifiedAt = null,
                twoFactorEnabled = false,
                lastPasswordChangeAt = null,
                deviceId = null,
                ipAddress = null
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
                .replace(Regex("[^a-z0-9_.]"), "") // Keep only valid characters
                .take(MAX_USERNAME_LENGTH)
                .let { if (it.length < MIN_USERNAME_LENGTH) it + "user" else it }
            email.isNotBlank() -> email
                .substringBefore("@")
                .lowercase()
                .replace(Regex("[^a-z0-9_.]"), "")
                .take(MAX_USERNAME_LENGTH)
                .let { if (it.length < MIN_USERNAME_LENGTH) it + "user" else it }
            else -> "user${System.currentTimeMillis()}"
        }
    }

    companion object {
        private const val COLLECTION_USERS = "users"
        private const val MAX_USERNAME_LENGTH = 30
        private const val MIN_USERNAME_LENGTH = 3
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}