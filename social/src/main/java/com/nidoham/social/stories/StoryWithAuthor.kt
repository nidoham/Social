package com.nidoham.social.stories

import com.nidoham.social.user.User

/**
 * Data class that combines Story with its Author User
 * Used for UI/UX to display stories with author information
 */
data class StoryWithAuthor(
    val story: Story,
    val author: User
) {
    /**
     * Quick access to story properties
     */
    val storyId: String get() = story.id
    val caption: String? get() = story.caption
    val mediaUrls: List<String> get() = story.mediaUrls
    val contentType: Story.ContentType get() = Story.ContentType.fromString(story.contentType)
    val createdAt: Long get() = story.createdAt
    val expiresAt: Long get() = story.expiresAt
    val viewsCount: Int get() = story.viewsCount
    val reactionsCount: Int get() = story.reactionsCount

    /**
     * Quick access to author properties
     */
    val authorId: String get() = author.id
    val authorName: String get() = author.name
    val authorUsername: String get() = author.username
    val authorAvatar: String? get() = author.avatarUrl
    val isAuthorVerified: Boolean get() = author.verified
    val isAuthorPremium: Boolean get() = author.premium

    /**
     * Check if story is active
     */
    fun isActive(): Boolean = story.isActive()

    /**
     * Check if story has expired
     */
    fun isExpired(): Boolean = story.isExpired()

    /**
     * Get time remaining until expiry
     */
    fun getTimeRemaining(): Long = story.getTimeRemaining()

    /**
     * Get hours remaining
     */
    fun getHoursRemaining(): Int = story.getHoursRemaining()

    /**
     * Check if a user can view this story
     */
    fun canUserView(userId: String, isFriend: Boolean = false, isCloseFriend: Boolean = false): Boolean {
        return story.canUserView(userId, isFriend, isCloseFriend)
    }

    /**
     * Format for display in UI
     */
    fun getDisplayInfo(): DisplayInfo {
        return DisplayInfo(
            storyId = storyId,
            authorName = authorName,
            authorUsername = "@${authorUsername}",
            authorAvatar = authorAvatar,
            isVerified = isAuthorVerified,
            caption = caption,
            mediaUrl = mediaUrls.firstOrNull(),
            timeRemaining = getHoursRemaining(),
            viewsCount = viewsCount,
            reactionsCount = reactionsCount,
            isPremium = isAuthorPremium
        )
    }

    /**
     * Data class for formatted display information
     */
    data class DisplayInfo(
        val storyId: String,
        val authorName: String,
        val authorUsername: String,
        val authorAvatar: String?,
        val isVerified: Boolean,
        val caption: String?,
        val mediaUrl: String?,
        val timeRemaining: Int,
        val viewsCount: Int,
        val reactionsCount: Int,
        val isPremium: Boolean
    )
}

/**
 * Extension function to group stories by author
 * Useful for displaying stories grouped by user in UI
 */
fun List<StoryWithAuthor>.groupByAuthor(): Map<User, List<Story>> {
    return this.groupBy({ it.author }, { it.story })
}

/**
 * Extension function to get unique authors from story list
 */
fun List<StoryWithAuthor>.getUniqueAuthors(): List<User> {
    return this.map { it.author }.distinctBy { it.id }
}

/**
 * Extension function to filter stories by author
 */
fun List<StoryWithAuthor>.filterByAuthor(authorId: String): List<StoryWithAuthor> {
    return this.filter { it.authorId == authorId }
}

/**
 * Extension function to get the first story for each unique author
 */
fun List<StoryWithAuthor>.getFirstStoryPerAuthor(): List<StoryWithAuthor> {
    return this.groupBy { it.authorId }
        .map { (_, stories) -> stories.first() }
}