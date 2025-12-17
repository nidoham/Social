package com.nidoham.social.model

/**
 * Holds the numerical statistics for a user's social activity.
 */
data class UserStats(
    val postCount: Int = 0,         // Default to 0
    val followerCount: Int = 0,     // Renamed for clarity (was 'followers')
    val followingCount: Int = 0     // Renamed for clarity (was 'following')
)