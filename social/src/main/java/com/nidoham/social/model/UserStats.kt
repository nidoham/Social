package com.nidoham.social.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlin.math.round

/**
 * Holds the numerical statistics for a user's social activity.
 * All counts must be non-negative.
 *
 * @property postCount Number of posts created by the user
 * @property followerCount Number of users following this user
 * @property followingCount Number of users this user is following
 * @property storyCount Number of active stories posted by the user
 * @property likeCount Total likes received across all content
 * @property commentCount Total comments received across all content
 */
data class UserStats(
    @get:PropertyName("postCount")
    @set:PropertyName("postCount")
    var postCount: Long = 0L,

    @get:PropertyName("followerCount")
    @set:PropertyName("followerCount")
    var followerCount: Long = 0L,

    @get:PropertyName("followingCount")
    @set:PropertyName("followingCount")
    var followingCount: Long = 0L,

    @get:PropertyName("storyCount")
    @set:PropertyName("storyCount")
    var storyCount: Long = 0L,

    @get:PropertyName("likeCount")
    @set:PropertyName("likeCount")
    var likeCount: Long = 0L,

    @get:PropertyName("commentCount")
    @set:PropertyName("commentCount")
    var commentCount: Long = 0L
) {
    /**
     * No-arg constructor required by Firestore
     */
    constructor() : this(0L, 0L, 0L, 0L, 0L, 0L)

    /**
     * Get follower-to-following ratio
     * @return Ratio, or null if followingCount is 0
     */
    @Exclude
    fun getFollowerRatio(): Double? {
        if (followingCount == 0L) return null
        return followerCount.toDouble() / followingCount.toDouble()
    }

    /**
     * Get total content count (posts + stories)
     * @return Sum of posts and stories
     */
    @Exclude
    fun getTotalContentCount(): Long = postCount + storyCount

    /**
     * Get total engagement (likes + comments)
     * @return Sum of all engagement metrics
     */
    @Exclude
    fun getTotalEngagement(): Long = likeCount + commentCount

    /**
     * Get average engagement per post
     * @return Average engagement, or 0 if no posts
     */
    @Exclude
    fun getAverageEngagementPerPost(): Double {
        if (postCount == 0L) return 0.0
        return getTotalEngagement().toDouble() / postCount.toDouble()
    }

    /**
     * Get engagement rate based on followers
     * @return Engagement rate as percentage (0-100+), or 0 if no followers
     */
    @Exclude
    fun getEngagementRate(): Double {
        if (followerCount == 0L) return 0.0
        return (getTotalEngagement().toDouble() / followerCount.toDouble()) * 100
    }

    /**
     * Check if user has any content
     * @return true if user has posted at least once
     */
    @Exclude
    fun hasContent(): Boolean = getTotalContentCount() > 0

    /**
     * Check if user follows anyone
     * @return true if following count > 0
     */
    @Exclude
    fun hasFollowing(): Boolean = followingCount > 0

    /**
     * Check if user has any followers
     * @return true if follower count > 0
     */
    @Exclude
    fun hasFollowers(): Boolean = followerCount > 0

    /**
     * Check if user is popular (has more followers than following)
     * @return true if follower count exceeds following count
     */
    @Exclude
    fun isPopular(): Boolean = followerCount > followingCount

    /**
     * Check if account appears to be a bot/spam (following way more than followers)
     * @return true if following count is 3x or more than follower count
     */
    @Exclude
    fun mightBeSpam(): Boolean {
        if (followingCount == 0L) return false
        return followingCount > followerCount * 3
    }

    /**
     * Get formatted follower count for display
     * @return Formatted string (e.g., "1.2K", "5M")
     */
    @Exclude
    fun getFormattedFollowerCount(): String = formatCount(followerCount)

    /**
     * Get formatted following count for display
     * @return Formatted string (e.g., "1.2K", "5M")
     */
    @Exclude
    fun getFormattedFollowingCount(): String = formatCount(followingCount)

    /**
     * Get formatted post count for display
     * @return Formatted string (e.g., "1.2K", "5M")
     */
    @Exclude
    fun getFormattedPostCount(): String = formatCount(postCount)

    /**
     * Increment post count
     * @return New UserStats with incremented post count
     */
    fun incrementPosts(): UserStats = copy(postCount = postCount + 1)

    /**
     * Decrement post count (minimum 0)
     * @return New UserStats with decremented post count
     */
    fun decrementPosts(): UserStats = copy(postCount = maxOf(0L, postCount - 1))

    /**
     * Increment story count
     * @return New UserStats with incremented story count
     */
    fun incrementStories(): UserStats = copy(storyCount = storyCount + 1)

    /**
     * Decrement story count (minimum 0)
     * @return New UserStats with decremented story count
     */
    fun decrementStories(): UserStats = copy(storyCount = maxOf(0L, storyCount - 1))

    /**
     * Increment follower count
     * @return New UserStats with incremented follower count
     */
    fun incrementFollowers(): UserStats = copy(followerCount = followerCount + 1)

    /**
     * Decrement follower count (minimum 0)
     * @return New UserStats with decremented follower count
     */
    fun decrementFollowers(): UserStats = copy(followerCount = maxOf(0L, followerCount - 1))

    /**
     * Increment following count
     * @return New UserStats with incremented following count
     */
    fun incrementFollowing(): UserStats = copy(followingCount = followingCount + 1)

    /**
     * Decrement following count (minimum 0)
     * @return New UserStats with decremented following count
     */
    fun decrementFollowing(): UserStats = copy(followingCount = maxOf(0L, followingCount - 1))

    /**
     * Increment like count
     * @param amount Amount to increment by (default 1)
     * @return New UserStats with incremented like count
     */
    fun incrementLikes(amount: Long = 1L): UserStats {
        require(amount >= 0) { "Amount must be non-negative" }
        return copy(likeCount = likeCount + amount)
    }

    /**
     * Decrement like count (minimum 0)
     * @param amount Amount to decrement by (default 1)
     * @return New UserStats with decremented like count
     */
    fun decrementLikes(amount: Long = 1L): UserStats {
        require(amount >= 0) { "Amount must be non-negative" }
        return copy(likeCount = maxOf(0L, likeCount - amount))
    }

    /**
     * Increment comment count
     * @param amount Amount to increment by (default 1)
     * @return New UserStats with incremented comment count
     */
    fun incrementComments(amount: Long = 1L): UserStats {
        require(amount >= 0) { "Amount must be non-negative" }
        return copy(commentCount = commentCount + amount)
    }

    /**
     * Decrement comment count (minimum 0)
     * @param amount Amount to decrement by (default 1)
     * @return New UserStats with decremented comment count
     */
    fun decrementComments(amount: Long = 1L): UserStats {
        require(amount >= 0) { "Amount must be non-negative" }
        return copy(commentCount = maxOf(0L, commentCount - amount))
    }

    /**
     * Reset all stats to zero
     * @return New UserStats with all counts set to 0
     */
    fun reset(): UserStats = UserStats()

    /**
     * Validate all stats are non-negative
     * @return true if all counts are >= 0
     */
    @Exclude
    fun isValid(): Boolean {
        return postCount >= 0 &&
                followerCount >= 0 &&
                followingCount >= 0 &&
                storyCount >= 0 &&
                likeCount >= 0 &&
                commentCount >= 0
    }

    companion object {
        /**
         * Format large numbers for display
         * @param count The number to format
         * @return Formatted string (e.g., "1.2K", "5M", "234")
         */
        fun formatCount(count: Long): String {
            return when {
                count < 1_000 -> count.toString()
                count < 1_000_000 -> {
                    val thousands = count / 1000.0
                    if (thousands >= 100) {
                        "${round(thousands).toInt()}K"
                    } else {
                        String.format("%.1fK", thousands)
                    }
                }
                count < 1_000_000_000 -> {
                    val millions = count / 1_000_000.0
                    if (millions >= 100) {
                        "${round(millions).toInt()}M"
                    } else {
                        String.format("%.1fM", millions)
                    }
                }
                else -> {
                    val billions = count / 1_000_000_000.0
                    String.format("%.1fB", billions)
                }
            }
        }

        /**
         * Create default stats for a new user
         * @return UserStats with all counts at 0
         */
        fun createDefault(): UserStats = UserStats()
    }
}