package com.nidoham.social.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * Represents the core User entity in the application.
 * This acts as an Aggregate Root for user-related data.
 *
 * Contains all user information including profile, statistics, and metadata.
 * This class is designed to work seamlessly with Cloud Firestore.
 *
 * @property id Firestore document ID (unique user identifier)
 * @property profile User's public profile information
 * @property stats User's engagement and activity statistics
 * @property metadata System-level metadata and account status
 */
data class User(
    @DocumentId
    var id: String? = null,

    @get:PropertyName("profile")
    @set:PropertyName("profile")
    var profile: UserProfile = UserProfile(),

    @get:PropertyName("stats")
    @set:PropertyName("stats")
    var stats: UserStats = UserStats(),

    @get:PropertyName("metadata")
    @set:PropertyName("metadata")
    var metadata: UserMetadata = UserMetadata()
) {
    /**
     * No-arg constructor required by Firestore
     */
    constructor() : this(
        id = null,
        profile = UserProfile(),
        stats = UserStats(),
        metadata = UserMetadata()
    )

    /**
     * Get user's display name or username as fallback
     * @return Display name if available, otherwise formatted username
     */
    @Exclude
    fun getDisplayNameOrUsername(): String {
        return profile.displayName.takeIf { it.isNotBlank() }
            ?: profile.getFormattedUsername()
    }

    /**
     * Get user's initials from display name
     * @return First letter of first two words, or first two letters if one word
     */
    @Exclude
    fun getInitials(): String {
        val name = profile.displayName.trim()
        if (name.isBlank()) return "?"

        val parts = name.split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    /**
     * Check if this user can view another user's private content
     * @param targetUserId The user ID whose content is being accessed
     * @return true if can view private content
     */
    @Exclude
    fun canViewPrivateContent(targetUserId: String?): Boolean {
        // User can always view their own content
        if (id == targetUserId) return true

        // Cannot view if target user is private and not self
        // In real app, would check friendship status here
        return false
    }

    /**
     * Check if user's account is fully set up
     * @return true if profile is complete and verified
     */
    @Exclude
    fun isAccountComplete(): Boolean {
        return profile.isComplete() && metadata.isFullyVerified()
    }

    /**
     * Check if user can perform actions (post, comment, etc.)
     * @return true if account is accessible and verified
     */
    @Exclude
    fun canPerformActions(): Boolean {
        return metadata.isAccessible() && metadata.isVerified
    }

    /**
     * Check if user can be followed
     * @return true if account is active and not banned
     */
    @Exclude
    fun canBeFollowed(): Boolean {
        return metadata.isActive && !metadata.isBanned
    }

    /**
     * Check if user is a new account (less than 7 days old)
     * @return true if account is less than 7 days old
     */
    @Exclude
    fun isNewAccount(): Boolean {
        return metadata.getAccountAgeInDays() < 7
    }

    /**
     * Check if user is verified (blue check)
     * @return true if user has verified badge
     */
    @Exclude
    fun hasVerifiedBadge(): Boolean {
        // In real app, this might be a separate field for official verification
        return metadata.isFullyVerified() && stats.followerCount >= 10000
    }

    /**
     * Check if user is online
     * @return true if user is currently online
     */
    @Exclude
    fun isOnline(): Boolean = metadata.isOnline()

    /**
     * Check if user profile is private
     * @return true if account is set to private
     */
    @Exclude
    fun isPrivateAccount(): Boolean = metadata.isPrivate

    /**
     * Get engagement score for ranking
     * @return Combined score based on follower count and engagement
     */
    @Exclude
    fun getInfluenceScore(): Double {
        val followerScore = stats.followerCount * 1.0
        val engagementScore = stats.getAverageEngagementPerPost() * 10.0
        val contentScore = stats.getTotalContentCount() * 0.5
        return followerScore + engagementScore + contentScore
    }

    /**
     * Validate that all user data is valid
     * @return true if all nested objects are valid
     */
    @Exclude
    fun isValid(): Boolean {
        return profile.isValid() &&
                stats.isValid() &&
                metadata.isAccessible()
    }

    /**
     * Update profile information
     * @return New User instance with updated profile
     */
    fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
        coverUrl: String? = null,
        website: String? = null,
        location: String? = null,
        pronouns: String? = null,
        gender: String? = null
    ): User {
        return copy(
            profile = profile.update(
                newDisplayName = displayName,
                newBio = bio,
                newAvatarUrl = avatarUrl,
                newCoverUrl = coverUrl,
                newWebsite = website,
                newLocation = location,
                newPronouns = pronouns,
                newGender = gender
            ),
            metadata = metadata.markAsUpdated()
        )
    }

    /**
     * Follow another user
     * @return New User instance with updated following count
     */
    fun follow(): User {
        return copy(
            stats = stats.incrementFollowing(),
            metadata = metadata.markAsUpdated()
        )
    }

    /**
     * Unfollow another user
     * @return New User instance with updated following count
     */
    fun unfollow(): User {
        return copy(
            stats = stats.decrementFollowing(),
            metadata = metadata.markAsUpdated()
        )
    }

    /**
     * Gain a follower
     * @return New User instance with updated follower count
     */
    fun gainFollower(): User {
        return copy(
            stats = stats.incrementFollowers(),
            metadata = metadata.markAsUpdated()
        )
    }

    /**
     * Lose a follower
     * @return New User instance with updated follower count
     */
    fun loseFollower(): User {
        return copy(
            stats = stats.decrementFollowers(),
            metadata = metadata.markAsUpdated()
        )
    }

    /**
     * Create a new post
     * @return New User instance with updated post count
     */
    fun createPost(): User {
        return copy(
            stats = stats.incrementPosts(),
            metadata = metadata.markAsUpdated()
        )
    }

    /**
     * Delete a post
     * @return New User instance with updated post count
     */
    fun deletePost(): User {
        return copy(
            stats = stats.decrementPosts(),
            metadata = metadata.markAsUpdated()
        )
    }

    /**
     * Create a new story
     * @return New User instance with updated story count
     */
    fun createStory(): User {
        return copy(
            stats = stats.incrementStories(),
            metadata = metadata.markAsUpdated()
        )
    }

    /**
     * Delete a story
     * @return New User instance with updated story count
     */
    fun deleteStory(): User {
        return copy(
            stats = stats.decrementStories(),
            metadata = metadata.markAsUpdated()
        )
    }

    /**
     * Login user
     * @param deviceId Optional device ID
     * @param ipAddress Optional IP address
     * @return New User instance with login recorded
     */
    fun login(deviceId: String? = null, ipAddress: String? = null): User {
        return copy(metadata = metadata.markLogin(deviceId, ipAddress))
    }

    /**
     * Logout user
     * @return New User instance with logout recorded
     */
    fun logout(): User {
        return copy(metadata = metadata.markLogout())
    }

    /**
     * Verify email
     * @return New User instance with email verified
     */
    fun verifyEmail(): User {
        return copy(metadata = metadata.markEmailVerified())
    }

    /**
     * Verify phone
     * @return New User instance with phone verified
     */
    fun verifyPhone(): User {
        return copy(metadata = metadata.markPhoneVerified())
    }

    /**
     * Toggle privacy setting
     * @return New User instance with privacy toggled
     */
    fun togglePrivacy(): User {
        return copy(metadata = metadata.togglePrivacy())
    }

    /**
     * Suspend account
     * @param reason Suspension reason
     * @param durationDays Duration in days
     * @return New User instance with suspension applied
     */
    fun suspend(reason: String, durationDays: Long): User {
        return copy(metadata = metadata.suspend(reason, durationDays))
    }

    /**
     * Ban account
     * @param reason Ban reason
     * @return New User instance with ban applied
     */
    fun ban(reason: String): User {
        return copy(metadata = metadata.ban(reason))
    }

    companion object {
        /**
         * Create a new user with required information
         * @param username Unique username
         * @param displayName User's display name
         * @param email User's email
         * @param avatarUrl URL to avatar image
         * @param birthDate Date of birth (timestamp in milliseconds)
         * @return New User instance
         */
        fun create(
            username: String,
            displayName: String,
            email: String,
            avatarUrl: String,
            birthDate: Long
        ): User {
            return User(
                profile = UserProfile.create(
                    username = username,
                    displayName = displayName,
                    email = email,
                    avatarUrl = avatarUrl,
                    birthDate = birthDate
                ),
                stats = UserStats.createDefault(),
                metadata = UserMetadata.createDefault()
            )
        }

        /**
         * Create a minimal user for testing
         * @return User with default/minimal values
         */
        fun createMinimal(): User {
            return User(
                profile = UserProfile(),
                stats = UserStats(),
                metadata = UserMetadata()
            )
        }
    }
}