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

            // 1. Build initial user object
            var user = buildUserFromFirebaseUser(firebaseUser, userId)

            // 2. Ensure username uniqueness before writing
            val isTaken = !isUsernameAvailable(user.profile.username).getOrDefault(true)
            if (isTaken) {
                // If generated username is taken, append random numbers
                val newProfile = user.profile.copy(
                    username = "${user.profile.username}${(100..999).random()}"
                )
                user = user.copy(profile = newProfile)
            }

            // 3. Save to Firestore
            usersCollection.document(userId)
                .set(user)
                .await()

            // 4. Save to Realtime Database
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
     * Update user (Generic)
     * Syncs changes to both Firestore and Realtime DB
     */
    suspend fun updateUser(userId: String, user: User): Result<Unit> {
        return try {
            // Update the metadata with current timestamp (Long)
            val updatedUser = user.copy(
                metadata = user.metadata.markAsUpdated() // Ensure this method in model returns Long for updatedAt
            )

            // Write to Firestore first (Source of Truth)
            usersCollection.document(userId)
                .set(updatedUser)
                .await()

            // Sync to Realtime DB
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
            val userResult = getUserById(userId)
            val user = userResult.getOrNull()
                ?: return Result.failure(Exception("User not found"))

            // Update using the model's method
            val updatedUser = user.login(deviceId, ipAddress)

            updateUser(userId, updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user profile specific fields
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
            val user = userResult.getOrNull()
                ?: return Result.failure(Exception("User not found"))

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

            updateUser(userId, updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Follow a user using a Firestore Transaction.
     * Ensures both the Current User and Target User are updated atomically.
     */
    suspend fun followUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val currentUserRef = usersCollection.document(currentUserId)
                val targetUserRef = usersCollection.document(targetUserId)

                val currentUserSnap = transaction.get(currentUserRef)
                val targetUserSnap = transaction.get(targetUserRef)

                val currentUser = currentUserSnap.toObject(User::class.java)
                    ?: throw Exception("Current user not found")
                val targetUser = targetUserSnap.toObject(User::class.java)
                    ?: throw Exception("Target user not found")

                // Apply logic
                val updatedCurrentUser = currentUser.follow()
                val updatedTargetUser = targetUser.gainFollower()

                // Write back to Firestore
                transaction.set(currentUserRef, updatedCurrentUser)
                transaction.set(targetUserRef, updatedTargetUser)

                // Note: We cannot transact Realtime DB here easily.
                // We update Firestore atomically, then update Realtime DB afterwards (best effort).
                // Returning the updated users to update Realtime DB outside the transaction block
                Pair(updatedCurrentUser, updatedTargetUser)
            }.await().let { (updatedCurrent, updatedTarget) ->
                // Sync to Realtime DB (Best Effort)
                usersRealtimeRef.child(currentUserId).setValue(updatedCurrent)
                usersRealtimeRef.child(targetUserId).setValue(updatedTarget)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unfollow a user using a Firestore Transaction.
     */
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val currentUserRef = usersCollection.document(currentUserId)
                val targetUserRef = usersCollection.document(targetUserId)

                val currentUserSnap = transaction.get(currentUserRef)
                val targetUserSnap = transaction.get(targetUserRef)

                val currentUser = currentUserSnap.toObject(User::class.java)
                    ?: throw Exception("Current user not found")
                val targetUser = targetUserSnap.toObject(User::class.java)
                    ?: throw Exception("Target user not found")

                // Apply logic
                val updatedCurrentUser = currentUser.unfollow()
                val updatedTargetUser = targetUser.loseFollower()

                transaction.set(currentUserRef, updatedCurrentUser)
                transaction.set(targetUserRef, updatedTargetUser)

                Pair(updatedCurrentUser, updatedTargetUser)
            }.await().let { (updatedCurrent, updatedTarget) ->
                usersRealtimeRef.child(currentUserId).setValue(updatedCurrent)
                usersRealtimeRef.child(targetUserId).setValue(updatedTarget)
            }

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

            // Note: This query requires a Firestore Index on 'profile.username'
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
        // FIXED: Changed Date() to System.currentTimeMillis() (Long)
        val currentTime: Long = System.currentTimeMillis()
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
                birthDate = null,
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
                createdAt = currentTime, // Long
                updatedAt = currentTime, // Long
                lastLoginAt = currentTime, // Long
                lastLogoutAt = null,
                isVerified = firebaseUser.isEmailVerified,
                isActive = true,
                isBanned = false,
                isPrivate = false,
                suspensionReason = null,
                suspendedUntil = null,
                emailVerifiedAt = if (firebaseUser.isEmailVerified) currentTime else null, // Long
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