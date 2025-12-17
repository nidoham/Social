package com.nidoham.social.model

/**
 * Represents the core User entity in the application.
 * This acts as an Aggregate Root for user-related data.
 */
data class User(
    val id: String,
    val profile: UserProfile,
    val stats: UserStats,
    val metadata: UserMetadata
)