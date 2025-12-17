package com.nidoham.social.model

/**
 * Contains the public-facing profile information of a user.
 */
data class UserProfile(
    val username: String,           // Unique handle (e.g., @nidoham)
    val displayName: String,        // Full display name (was 'name')
    val email: String,
    val avatarUrl: String,          // Renamed from 'profilePic' for clarity
    val coverUrl: String,           // Renamed from 'coverPic'
    val bio: String? = null,        // Nullable: User might not have a bio
    val website: String? = null,    // Added: Common in social apps
    val gender: String? = null,     // Renamed from 'sex', Nullable
    val pronouns: String? = null,   // Renamed to plural 'pronouns', Nullable
    val birthDate: Long
)