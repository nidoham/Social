package com.nidoham.social.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import com.nidoham.social.model.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val realtimeDb: FirebaseDatabase
) {

    /**
     * Creates a new user record in Firestore based on the Firebase Auth User.
     * This should be called immediately after a successful sign-up.
     */
    suspend fun createAccount(firebaseUser: FirebaseUser): Result<Boolean> {
        return try {
            val uid = firebaseUser.uid
            val currentTime = System.currentTimeMillis()

            val name: String = firebaseUser.displayName ?: "New User"
            val email: String = firebaseUser.email ?: ""
            val photoUrl: String = firebaseUser.photoUrl?.toString() ?: ""

            val newUser = User(
                id = uid,
                profile = UserProfile(
                    username = name.lowercase(),
                    displayName = name,
                    email = email,
                    avatarUrl = photoUrl,
                    coverUrl = photoUrl,
                    bio = null,
                    website = null,
                    gender = null,
                    pronouns = null,
                    birthDate = 0L
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

            firestore.collection("users")
                .document(uid)
                .set(newUser)
                .await() // Suspends until the operation completes

            Result.success(true)

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}