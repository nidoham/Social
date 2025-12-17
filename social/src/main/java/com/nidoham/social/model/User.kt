package com.nidoham.social.model

/**
 * Represents the core User entity in the application.
 * This acts as an Aggregate Root for user-related data.
 */
data class User(
    val id: String,                 // Standard naming 'id' instead of 'uid'
    val profile: UserProfile,       // Personal details (Name, Bio, Avatar)
    val stats: UserStats,           // Social counts (Followers, Posts)
    val metadata: UserMetadata      // System info (Timestamps, Verification)
)